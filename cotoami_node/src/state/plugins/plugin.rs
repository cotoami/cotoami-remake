use std::path::Path;

use anyhow::Result;
use cotoami_plugin_api::*;
use extism::*;
use tracing::debug;

pub struct Plugin {
    plugin: extism::Plugin,
    metadata: Metadata,
}

impl Plugin {
    const FILE_NAME_SUFFIX: &'static str = ".wasm";

    pub fn new<P: AsRef<Path>>(wasm_file: P) -> Result<Self> {
        let wasm_file = wasm_file.as_ref();
        debug!("Loading a plugin: {wasm_file:?}");
        let manifest = Manifest::new([Wasm::file(&wasm_file)]);
        let mut plugin = extism::Plugin::new(&manifest, [], true)?;
        let metadata = plugin.call::<(), Metadata>("metadata", ())?;
        Ok(Self { plugin, metadata })
    }

    pub fn is_plugin_file<P: AsRef<Path>>(path: P) -> bool {
        path.as_ref()
            .file_name()
            .map(|name| {
                name.to_str()
                    .map(|name| name.ends_with(Plugin::FILE_NAME_SUFFIX))
                    .unwrap_or(false)
            })
            .unwrap_or(false)
    }

    pub fn metadata(&self) -> &Metadata { &self.metadata }

    pub fn identifier(&self) -> &str { &self.metadata.identifier }

    pub fn init(&mut self, config: &Config) -> Result<()> {
        self.plugin.call::<&Config, ()>("init", config)
    }

    pub fn on(&mut self, event: &Event) -> Result<()> {
        self.plugin.call::<&Event, ()>("on", event)
    }

    pub fn destroy(&mut self) -> Result<()> { self.plugin.call::<(), ()>("destroy", ()) }
}
