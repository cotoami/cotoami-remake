use std::{
    collections::{HashMap, VecDeque},
    hash::Hash,
    pin::Pin,
    sync::{Arc, Weak},
};

use futures::{
    task::{Context, Poll, Waker},
    Stream,
};
use parking_lot::Mutex;
use smallvec::SmallVec;

pub struct Publisher<Message, Topic> {
    state: Arc<Mutex<PublisherState<Message, Topic>>>,
}

impl<Message: Clone, Topic: Eq + Hash> Publisher<Message, Topic> {
    pub fn new() -> Self {
        let state = PublisherState {
            next_subscriber_id: 0,
            subscribers: HashMap::new(),
            topics: HashMap::new(),
        };
        Publisher {
            state: Arc::new(Mutex::new(state)),
        }
    }

    #[allow(dead_code)] // used only in tests
    pub fn count_subscribers(&self) -> usize { self.state.lock().subscribers.len() }

    pub fn subscribe(&mut self, topic: Option<impl Into<Topic>>) -> Subscriber<Message, Topic> {
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
            onetime: false,
            enabled: true,
        };
        let sub_state = Arc::new(Mutex::new(sub_state));

        {
            let mut state = self.state.lock();
            state.subscribers.insert(sub_id, Arc::clone(&sub_state));
            if let Some(topic) = topic {
                state
                    .topics
                    .entry(topic.into())
                    .or_insert_with(|| vec![])
                    .push(sub_id);
            }
        }

        Subscriber {
            state: sub_state,
            publisher_state: Arc::downgrade(&self.state),
        }
    }

    pub fn subscribe_onetime(
        &mut self,
        topic: Option<impl Into<Topic>>,
    ) -> Subscriber<Message, Topic> {
        let sub = self.subscribe(topic);
        sub.state.lock().onetime = true;
        sub
    }

    pub fn publish(&mut self, message: &Message, topic: Option<&Topic>) {
        let state = self.state.lock();

        let subscribers = if let Some(topic) = topic {
            state
                .topics
                .get(topic)
                .unwrap_or(&vec![])
                .iter()
                .map(|id| state.subscribers.get(id))
                .flatten()
                .collect::<Vec<_>>()
        } else {
            state.subscribers.values().collect::<Vec<_>>()
        };

        let wakers = subscribers
            .iter()
            .flat_map(|subscriber| {
                let mut subscriber = subscriber.lock();
                subscriber.send_message(message.clone())
            })
            .collect::<Vec<_>>();
        wakers.into_iter().for_each(|waker| waker.wake());
    }
}

impl<Message, Topic> Drop for Publisher<Message, Topic> {
    fn drop(&mut self) {
        let state = self.state.lock();
        for subscriber in state.subscribers.values() {
            let mut subscriber = subscriber.lock();
            subscriber.enabled = false; // the end of the stream
        }
    }
}

pub struct PublisherState<Message, Topic> {
    pub next_subscriber_id: usize,
    pub subscribers: HashMap<usize, Arc<Mutex<SubscriberState<Message>>>>,
    pub topics: HashMap<Topic, Vec<usize>>,
}

impl<Message, Topic> PublisherState<Message, Topic> {
    fn unsubscribe(&mut self, subscriber_id: &usize) {
        self.subscribers.remove(subscriber_id);
        for sub_ids in self.topics.values_mut() {
            sub_ids.retain(|id| id != subscriber_id);
        }
    }
}

pub struct Subscriber<Message, Topic> {
    state: Arc<Mutex<SubscriberState<Message>>>,
    publisher_state: Weak<Mutex<PublisherState<Message, Topic>>>,
}

impl<Message, Topic> Subscriber<Message, Topic> {
    fn unsubscribe(&self) {
        let mut sub_state = self.state.lock();
        sub_state.enabled = false;

        if let Some(pub_state) = self.publisher_state.upgrade() {
            let mut pub_state = pub_state.lock();
            pub_state.unsubscribe(&sub_state.id);
        }
    }
}

impl<Message, Topic> Drop for Subscriber<Message, Topic> {
    fn drop(&mut self) { self.unsubscribe(); }
}

impl<Message, Topic> Stream for Subscriber<Message, Topic> {
    type Item = Message;

    fn poll_next(self: Pin<&mut Self>, context: &mut Context) -> Poll<Option<Message>> {
        let mut state = self.state.lock();
        if !state.enabled {
            Poll::Ready(None)
        } else if let Some(next_message) = state.message_queue.pop_front() {
            if state.onetime {
                drop(state);
                self.unsubscribe();
            }
            Poll::Ready(Some(next_message))
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
    pub onetime: bool,
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
    use futures::StreamExt;

    use super::*;

    #[tokio::test]
    async fn pubsub() {
        let mut publisher = Publisher::<String, String>::new();

        let mut sub1 = publisher.subscribe(None::<String>);
        let mut sub2 = publisher.subscribe(Some("animal"));
        let mut sub3 = publisher.subscribe(Some("plant"));

        publisher.publish(&"hello".into(), None);
        publisher.publish(&"cat".into(), Some(&"animal".into()));
        publisher.publish(&"clover".into(), Some(&"plant".into()));

        assert_eq!(sub1.next().await, Some("hello".into()));

        assert_eq!(sub2.next().await, Some("hello".into()));
        assert_eq!(sub2.next().await, Some("cat".into()));

        assert_eq!(sub3.next().await, Some("hello".into()));
        assert_eq!(sub3.next().await, Some("clover".into()));
    }

    #[tokio::test]
    async fn onetime_subscriber() {
        let mut publisher = Publisher::<String, String>::new();
        let mut sub = publisher.subscribe_onetime(Some("animal"));

        publisher.publish(&"cat".into(), Some(&"animal".into()));
        publisher.publish(&"dog".into(), Some(&"animal".into()));

        assert_eq!(sub.next().await, Some("cat".into()));
        assert_eq!(sub.next().await, None);
    }

    #[tokio::test]
    async fn publisher_dropped() {
        let mut sub = {
            let mut publisher = Publisher::<String, String>::new();
            publisher.subscribe(None::<String>)
        };
        assert_eq!(sub.next().await, None);
    }

    #[tokio::test]
    async fn subscriber_dropped() {
        let mut publisher = Publisher::<String, String>::new();
        let _sub1 = publisher.subscribe(None::<String>);
        {
            let _sub2 = publisher.subscribe(None::<String>);
            assert_eq!(publisher.count_subscribers(), 2);
        }
        assert_eq!(publisher.count_subscribers(), 1);
    }
}
