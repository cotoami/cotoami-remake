//! Performance test of graph traversal from a root cotonoma in a given database.

use std::time::Instant;

use anyhow::{anyhow, Result};
use clap::Parser;
use cotoami_db::prelude::*;

fn main() -> Result<()> {
    let args = Args::parse();

    let db = args.db()?;

    let mut ds = db.new_session()?;
    let node = ds.local_node()?;
    println!("Node name: {}", node.name);
    let (_, root_coto) = ds
        .local_node_root()?
        .ok_or(anyhow!("The root cotonoma is required."))?;

    test_traversal(&mut ds, &root_coto, &args)?;
    println!();
    test_traversal_by_cte(&mut ds, &root_coto, &args)?;

    Ok(())
}

#[derive(Parser, Debug)]
#[command(author, version)]
#[command(about = "Performance test of graph traversal.", long_about = None)]
struct Args {
    db_dir: String,

    #[arg(short, long)]
    all: bool,

    #[arg(short, long, value_name = "NUMBER_OF_TIMES", default_value_t = 3)]
    warmup: u8,
}

impl Args {
    fn db(&self) -> Result<Database> { Database::new(&self.db_dir) }
}

fn test_traversal(ds: &mut DatabaseSession<'_>, root: &Coto, args: &Args) -> Result<()> {
    println!("Traversing by level queries...");
    let until_cotonoma = !args.all;
    warm_up_traversal(ds, root, until_cotonoma, args.warmup)?;
    let root = root.clone();
    let start = Instant::now();
    let graph = ds.graph(root, until_cotonoma)?;
    println!(
        "Graph: {} cotos, {} links (elapsed: {:?})",
        graph.count_cotos(),
        graph.count_links(),
        start.elapsed()
    );
    Ok(())
}

fn test_traversal_by_cte(ds: &mut DatabaseSession<'_>, root: &Coto, args: &Args) -> Result<()> {
    println!("Traversing by recursive CTE...");
    let until_cotonoma = !args.all;
    warm_up_traversal_by_cte(ds, &root, until_cotonoma, args.warmup)?;
    let root = root.clone();
    let start = Instant::now();
    let graph = ds.graph_by_cte(root, until_cotonoma)?;
    println!(
        "Graph: {} cotos, {} links (elapsed: {:?})",
        graph.count_cotos(),
        graph.count_links(),
        start.elapsed()
    );
    Ok(())
}

fn warm_up_traversal(
    ds: &mut DatabaseSession<'_>,
    root: &Coto,
    until_cotonoma: bool,
    number_of_times: u8,
) -> Result<()> {
    println!("Warming up traversal {number_of_times} times...");
    for _ in 0..number_of_times {
        ds.graph(root.clone(), until_cotonoma)?;
    }
    Ok(())
}

fn warm_up_traversal_by_cte(
    ds: &mut DatabaseSession<'_>,
    root: &Coto,
    until_cotonoma: bool,
    number_of_times: u8,
) -> Result<()> {
    println!("Warming up traversal_by_cte {number_of_times} times...");
    for _ in 0..number_of_times {
        ds.graph_by_cte(root.clone(), until_cotonoma)?;
    }
    Ok(())
}
