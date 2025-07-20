use anyhow::{anyhow, bail, Result};
use cotoami_plugin_api::*;
use extism_pdk::*;
use lazy_regex::*;

const IDENTIFIER: &'static str = "app.cotoami.plugin.geocoder";
const NAME: &'static str = "Geocoder";

static QUERY_PATTERN: Lazy<Regex> = lazy_regex!(r"(?m)^#location\s(.*)$");

#[host_fn]
extern "ExtismHost" {
    fn log(message: String);
    fn info(message: String);
    fn edit_coto(id: String, diff: CotoContentDiff) -> Coto;
}

#[plugin_fn]
pub fn metadata() -> FnResult<Metadata> {
    Ok(Metadata::new(IDENTIFIER, NAME, env!("CARGO_PKG_VERSION"))
        .mark_as_agent(NAME, Vec::from(include_bytes!("icon.png"))))
}

#[plugin_fn]
pub fn init(config: Config) -> FnResult<()> {
    unsafe { log(format!("{IDENTIFIER}: init with: {config:?}"))? };
    Ok(())
}

#[plugin_fn]
pub fn on(event: Event) -> FnResult<()> {
    match event {
        Event::CotoPosted {
            coto,
            local_node_id,
        } => {
            geocode(&coto, &local_node_id)?;
        }
        Event::CotoUpdated {
            coto,
            local_node_id,
        } => {
            geocode(&coto, &local_node_id)?;
        }
    }
    Ok(())
}

#[plugin_fn]
pub fn destroy() -> FnResult<()> {
    Ok(())
}

/// Nominatim API response structure
/// Maps the API field names to our internal structure
#[derive(Debug, serde::Deserialize)]
struct NominatimResult {
    lat: String,
    lon: String,
    display_name: String,
}

impl NominatimResult {
    fn convert_to_geolocation(&self) -> Result<Geolocation> {
        let latitude: f64 = self
            .lat
            .parse()
            .map_err(|e| anyhow!("Invalid latitude value '{}': {}", self.lat, e))?;
        let longitude: f64 = self
            .lon
            .parse()
            .map_err(|e| anyhow!("Invalid longitude value '{}': {}", self.lon, e))?;
        if latitude < -90.0 || latitude > 90.0 {
            bail!("Latitude {} is out of valid range.", latitude);
        }
        if longitude < -180.0 || longitude > 180.0 {
            bail!("Longitude {} is out of valid range.", longitude);
        }
        Ok(Geolocation {
            latitude,
            longitude,
        })
    }
}

fn geocode(coto: &Coto, local_node_id: &str) -> Result<()> {
    let Some(query) = extract_query(&coto, &local_node_id) else {
        return Ok(());
    };
    let results = send_request_to_nominatim(&query)?;
    if let Some(result) = results.first() {
        unsafe { info(format!("{query:?} => {}", result.display_name))? };
        let mut diff = CotoContentDiff::default();
        diff.geolocation = Some(result.convert_to_geolocation()?);
        unsafe { edit_coto(coto.uuid.clone(), diff)? };
    } else {
        unsafe { info(format!("No results for: {query:?}"))? };
    }
    Ok(())
}

fn extract_query(coto: &Coto, local_node_id: &str) -> Option<String> {
    if coto.node_id != local_node_id {
        return None; // Ignore remote cotos
    }
    let Some(content) = &coto.content else {
        return None; // No content
    };
    if let Some(caps) = QUERY_PATTERN.captures(content) {
        if let Some(cap) = caps.get(1) {
            return Some(cap.as_str().trim().to_owned());
        }
    }
    None
}

const USER_AGENT: &'static str = concat!("cotoami-plugin-geocoder/", env!("CARGO_PKG_VERSION"));

fn send_request_to_nominatim(query: &str) -> Result<Vec<NominatimResult>> {
    let req = HttpRequest::new(format!(
        "https://nominatim.openstreetmap.org/search?q={}&format=json&limit=1",
        urlencoding::encode(query)
    ))
    .with_method("GET")
    .with_header("User-Agent", USER_AGENT)
    .with_header("Accept", "application/json");
    let res = http::request::<()>(&req, None)?;
    let status = res.status_code();
    if 200 <= status && status < 300 {
        res.json()
    } else {
        bail!("Status {status}");
    }
}
