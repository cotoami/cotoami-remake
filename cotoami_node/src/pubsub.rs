use futures::task::{Context, Poll, Waker};
use futures::Stream;
use parking_lot::Mutex;
use smallvec::SmallVec;
use std::collections::{HashMap, VecDeque};
use std::pin::Pin;
use std::sync::Arc;
use std::sync::Weak;

pub struct Publisher<Message> {
    state: Arc<Mutex<PublisherState<Message>>>,
}

impl<Message: Clone> Publisher<Message> {
    pub fn new() -> Self {
        let state = PublisherState {
            next_subscriber_id: 0,
            subscribers: HashMap::new(),
        };
        Publisher {
            state: Arc::new(Mutex::new(state)),
        }
    }

    pub fn count_subscribers(&self) -> usize {
        self.state.lock().subscribers.len()
    }

    pub fn subscribe(&mut self) -> Subscriber<Message> {
        let sub_id = {
            let mut state = self.state.lock();
            let id = state.next_subscriber_id;
            state.next_subscriber_id += 1;
            id
        };

        let sub_state = SubscriberState {
            id: sub_id,
            message_queue: VecDeque::new(),
            on_message_received: vec![],
            enabled: true,
        };
        let sub_state = Arc::new(Mutex::new(sub_state));

        {
            let mut state = self.state.lock();
            state.subscribers.insert(sub_id, Arc::clone(&sub_state));
        }

        Subscriber {
            state: sub_state,
            publisher_state: Arc::downgrade(&self.state),
        }
    }

    pub fn publish(&mut self, message: &Message) {
        let state = self.state.lock();
        let wakers = state
            .subscribers
            .iter()
            .flat_map(|(_id, subscriber)| {
                let mut subscriber = subscriber.lock();
                subscriber.send_message(message.clone())
            })
            .collect::<Vec<_>>();
        wakers.into_iter().for_each(|waker| waker.wake());
    }
}

impl<Message> Drop for Publisher<Message> {
    fn drop(&mut self) {
        let state = self.state.lock();
        for subscriber in state.subscribers.values() {
            let mut subscriber = subscriber.lock();
            subscriber.enabled = false; // the end of the stream
        }
    }
}

pub struct PublisherState<Message> {
    pub next_subscriber_id: usize,
    pub subscribers: HashMap<usize, Arc<Mutex<SubscriberState<Message>>>>,
}

pub struct Subscriber<Message> {
    state: Arc<Mutex<SubscriberState<Message>>>,
    publisher_state: Weak<Mutex<PublisherState<Message>>>,
}

impl<Message> Drop for Subscriber<Message> {
    fn drop(&mut self) {
        // unsubscribe this subscriber
        if let Some(pub_state) = self.publisher_state.upgrade() {
            let mut pub_state = pub_state.lock();
            let sub_state = self.state.lock();
            pub_state.subscribers.remove(&sub_state.id);
        }
    }
}

impl<Message> Stream for Subscriber<Message> {
    type Item = Message;

    fn poll_next(self: Pin<&mut Self>, context: &mut Context) -> Poll<Option<Message>> {
        let mut state = self.state.lock();
        if let Some(next_message) = state.message_queue.pop_front() {
            Poll::Ready(Some(next_message))
        } else if !state.enabled {
            Poll::Ready(None)
        } else {
            state.on_message_received.push(context.waker().clone());
            Poll::Pending
        }
    }
}

pub struct SubscriberState<Message> {
    pub id: usize,
    pub message_queue: VecDeque<Message>,
    pub on_message_received: Vec<Waker>,
    pub enabled: bool,
}

impl<Message> SubscriberState<Message> {
    fn send_message(&mut self, message: Message) -> SmallVec<[Waker; 8]> {
        self.message_queue.push_back(message);
        self.on_message_received
            .drain(..)
            .collect::<SmallVec<[_; 8]>>()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use futures::StreamExt;

    #[tokio::test]
    async fn pubsub() {
        let mut publisher = Publisher::<String>::new();

        let mut sub1 = publisher.subscribe();
        let mut sub2 = publisher.subscribe();

        publisher.publish(&"hello".into());
        publisher.publish(&"world".into());

        assert_eq!(sub1.next().await, Some("hello".into()));
        assert_eq!(sub1.next().await, Some("world".into()));

        assert_eq!(sub2.next().await, Some("hello".into()));
        assert_eq!(sub2.next().await, Some("world".into()));
    }

    #[tokio::test]
    async fn publisher_dropped() {
        let mut sub = {
            let mut publisher = Publisher::<String>::new();
            publisher.subscribe()
        };
        assert_eq!(sub.next().await, None);
    }

    #[tokio::test]
    async fn subscriber_dropped() {
        let mut publisher = Publisher::<String>::new();
        let _sub1 = publisher.subscribe();
        {
            let _sub2 = publisher.subscribe();
            assert_eq!(publisher.count_subscribers(), 2);
        }
        assert_eq!(publisher.count_subscribers(), 1);
    }
}
