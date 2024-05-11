//! A CLI tool to perform full-text search in a given database.

use std::time::Instant;

use anyhow::Result;
use clap::Parser;
use cotoami_db::prelude::*;

fn main() -> Result<()> {
    let args = Args::parse();

    let db = args.db()?;
    let mut ds = db.new_session()?;

    let start = Instant::now();
    let results = ds.search_cotos(&args.query, None, None, args.limit, 0)?;
    println!(
        "Found {} cotos by \"{}\" (elapsed: {:?})",
        results.total_rows,
        &args.query,
        start.elapsed()
    );
    println!("{}", serde_json::to_string_pretty(&results.rows)?);

    Ok(())
}

#[derive(Parser, Debug)]
#[command(author, version)]
#[command(about = "Perform full-text search.", long_about = None)]
struct Args {
    db_dir: String,

    query: String,

    #[arg(short, long, default_value_t = 10)]
    limit: i64,
}

impl Args {
    fn db(&self) -> Result<Database> { Database::new(&self.db_dir) }
}
