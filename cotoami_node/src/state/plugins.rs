use std::{collections::HashMap, path::Path, sync::Arc};

use anyhow::Result;
use parking_lot::{Mutex, RwLock};

pub type Plugin = Arc<Mutex<extism::Plugin>>;

#[derive(Debug, Clone, Default)]
pub struct Plugins {
    plugins: Arc<RwLock<HashMap<String, Plugin>>>,
}

impl Plugins {
    pub fn load<P: AsRef<Path>>(plugins_dir: P) -> Result<Self> { Ok(Self::default()) }
}
