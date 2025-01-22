use std::{default::Default, time::Duration};

pub trait RetryPolicy {
    /// Returns the next delay calculated from the last retry number and duration.
    fn next_delay(&self, last_retry: Option<(usize, Duration)>) -> Option<Duration>;
}

#[derive(Debug)]
pub struct ExponentialBackoff {
    initial_delay: Duration,
    factor: f64,
    max_duration: Option<Duration>,
    max_retries: Option<usize>,
}

impl ExponentialBackoff {
    pub const fn new(
        initial_delay: Duration,
        factor: f64,
        max_duration: Option<Duration>,
        max_retries: Option<usize>,
    ) -> Self {
        Self {
            initial_delay,
            factor,
            max_duration,
            max_retries,
        }
    }
}

impl RetryPolicy for ExponentialBackoff {
    fn next_delay(&self, last_retry: Option<(usize, Duration)>) -> Option<Duration> {
        if let Some((retry_num, last_duration)) = last_retry {
            if let Some(max_retries) = self.max_retries {
                if retry_num >= max_retries {
                    return None;
                }
            }
            let duration = last_duration.mul_f64(self.factor);
            if let Some(max_duration) = self.max_duration {
                Some(duration.min(max_duration))
            } else {
                Some(duration)
            }
        } else {
            Some(self.initial_delay)
        }
    }
}

const DEFAULT_RETRY_POLICY: ExponentialBackoff = ExponentialBackoff::new(
    Duration::from_millis(300),    // first delay
    2.,                            // factor
    Some(Duration::from_secs(10)), // max delay
    None,                          // infinite retries
);

pub type BoxedRetry = Box<dyn RetryPolicy + Send + Unpin + 'static>;

pub struct RetryState {
    retry_policy: BoxedRetry,
    last_retry: Option<(usize, Duration)>,
}

impl RetryState {
    pub fn new(retry_policy: BoxedRetry) -> Self {
        Self {
            retry_policy,
            last_retry: None,
        }
    }

    pub fn next_delay(&mut self) -> Option<Duration> {
        if let Some(delay) = self.retry_policy.next_delay(self.last_retry) {
            self.last_retry = Some((self.last_number() + 1, delay));
            Some(delay)
        } else {
            None
        }
    }

    pub fn last_number(&self) -> usize { self.last_retry.map(|retry| retry.0).unwrap_or(0) }
}

impl Default for RetryState {
    fn default() -> Self { RetryState::new(Box::new(DEFAULT_RETRY_POLICY)) }
}
