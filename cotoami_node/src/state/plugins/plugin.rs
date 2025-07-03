use std::{path::Path, sync::Arc};

use anyhow::Result;
use cotoami_plugin_api::*;
use extism::*;
use parking_lot::Mutex;
use tracing::info;

use crate::state::{
    plugins::{configs::Configs, host_fn::HostFnContext},
    NodeState,
};

#[derive(Clone)]
pub struct Plugin {
    plugin: Arc<Mutex<extism::Plugin>>,
    metadata: Arc<Metadata>,
}

impl Plugin {
    const FILE_NAME_SUFFIX: &'static str = ".wasm";

    pub fn new<P: AsRef<Path>>(
        wasm_file: P,
        configs: &Configs,
        node_state: NodeState,
    ) -> Result<Self> {
        // Building a plugin needs plugin metadata (identifier) to register host functions
        // with `HostFnContext`, but you need the plugin to get its metadata. To resolve
        // this egg or chicken situation, it requires a bit tricky way to build a plugin:
        //   1) First, load the real metadata using dummy metadata.
        //   2) Then, build a plugin with the loaded metadata.

        let metadata = Self::build(
            wasm_file.as_ref(),
            "",
            Config::default(),
            node_state.clone(),
        )?
        .call::<(), Metadata>("metadata", ())?;
        let identifier = metadata.identifier.clone();
        let config = configs.config(&identifier);

        let plugin = Self::build(
            wasm_file.as_ref(),
            metadata.identifier.clone(),
            config,
            node_state.clone(),
        )?;
        Ok(Self {
            plugin: Arc::new(Mutex::new(plugin)),
            metadata: Arc::new(metadata),
        })
    }

    fn build<P: AsRef<Path>>(
        wasm_file: P,
        identifier: impl Into<String>,
        config: Config,
        node_state: NodeState,
    ) -> Result<extism::Plugin> {
        let ctx = HostFnContext::new(identifier.into(), node_state);
        let manifest = Manifest::new([Wasm::file(wasm_file)])
            .with_config(
                config
                    .iter()
                    .map(|(key, value)| (key, Config::to_string(value))),
            )
            .with_allowed_hosts(config.allowed_hosts().into_iter());
        PluginBuilder::new(manifest)
            .with_wasi(true)
            .with_function("log", [PTR], [], UserData::new(()), log)
            .with_function("version", [], [PTR], ctx.new_user_data(), version)
            .with_function(
                "post_coto",
                [PTR, PTR],
                [PTR],
                ctx.new_user_data(),
                post_coto,
            )
            .with_function(
                "edit_coto",
                [PTR, PTR],
                [PTR],
                ctx.new_user_data(),
                edit_coto,
            )
            .with_function("create_ito", [PTR], [PTR], ctx.new_user_data(), create_ito)
            .with_function(
                "ancestors_of",
                [PTR],
                [PTR],
                ctx.new_user_data(),
                ancestors_of,
            )
            .build()
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

    pub fn init(&self, config: &Config) -> Result<()> {
        self.plugin.lock().call::<&Config, ()>("init", config)
    }

    pub fn on(&self, event: &Event) -> Result<()> {
        self.plugin.lock().call::<&Event, ()>("on", event)
    }

    pub fn destroy(&self) -> Result<()> { self.plugin.lock().call::<(), ()>("destroy", ()) }
}

// fn log(message: String)
host_fn!(log(_user_data: (); message: String) {
    info!(message);
    Ok(())
});

// fn version() -> String
host_fn!(version(context: HostFnContext;) -> String {
    let context = context.get()?;
    let context = context.lock().unwrap();
    Ok(context.node_state.version().to_owned())
});

// fn post_coto(input: CotoInput, post_to: Option<String>) -> Coto
host_fn!(post_coto(context: HostFnContext; input: CotoInput, post_to: Option<String>) -> Coto {
    let context = context.get()?;
    let context = context.lock().unwrap();
    context.post_coto(input, post_to)
});

// fn edit_coto(id: String, diff: CotoContentDiff) -> Coto
host_fn!(edit_coto(context: HostFnContext; id: String, diff: CotoContentDiff) -> Coto {
    let context = context.get()?;
    let context = context.lock().unwrap();
    context.edit_coto(id, diff)
});

// fn create_ito(input: ItoInput) -> Ito
host_fn!(create_ito(context: HostFnContext; input: ItoInput) -> Ito {
    let context = context.get()?;
    let context = context.lock().unwrap();
    context.create_ito(input)
});

// fn ancestors_of(coto_id: String) -> Ancestors
host_fn!(ancestors_of(context: HostFnContext; coto_id: String) -> Ancestors {
    let context = context.get()?;
    let mut context = context.lock().unwrap();
    context.ancestors_of(coto_id)
});
