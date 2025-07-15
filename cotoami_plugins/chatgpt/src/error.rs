use extism_pdk::*;

#[derive(Debug, serde::Deserialize, FromBytes)]
#[encoding(Json)]
pub struct ErrorResponseBody {
    pub error: Error,
}

#[derive(Debug, serde::Deserialize, FromBytes)]
#[encoding(Json)]
pub struct Error {
    #[serde(rename = "type")]
    pub kind: String,
    pub message: String,

    pub code: Option<String>,
    pub param: Option<String>,
}
