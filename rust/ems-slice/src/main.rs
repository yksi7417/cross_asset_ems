//! `ems-slice --input <journal> --output <journal> [--seed <n>]`
//!
//! A pure function from an input journal to an output journal. No network, no
//! clock, no filesystem beyond those two paths. The Java and C++ binaries
//! accept the same arguments and must produce byte-identical output — see
//! `conformance/README.md`.
//!
//! Exit codes: `0` success, `1` the input journal could not be read, `2` a
//! usage error. These are part of the contract: the harness uses them to tell
//! "this implementation rejected the input" apart from "this implementation was
//! invoked wrongly".
#![forbid(unsafe_code)]

mod runner;

use std::path::PathBuf;
use std::process::ExitCode;

use ems_core::{read_journal, write_journal, DeterministicIds};

const EXIT_OK: u8 = 0;
const EXIT_INPUT_ERROR: u8 = 1;
const EXIT_USAGE: u8 = 2;

const USAGE: &str = "usage: ems-slice --input <journal> --output <journal> [--seed <n>]

  --input   input event journal (JSONL)
  --output  output event journal (JSONL), overwritten
  --seed    identifier generator seed, default 0

A pure function from input journal to output journal. See conformance/README.md.";

/// Parsed command line.
#[derive(Debug)]
struct Args {
    input: PathBuf,
    output: PathBuf,
    seed: u64,
}

fn main() -> ExitCode {
    // Argument parsing is hand-written: clap would be three dependencies for
    // four flags, and cargo-deny has to justify every one of them.
    let command_line: Vec<String> = std::env::args().skip(1).collect();
    let args = match parse_args(&command_line) {
        Ok(Some(args)) => args,
        Ok(None) => return ExitCode::from(EXIT_USAGE),
        Err(message) => {
            eprintln!("ems-slice: {message}");
            eprintln!("{USAGE}");
            return ExitCode::from(EXIT_USAGE);
        }
    };

    let events = match read_journal(&args.input) {
        Ok(events) => events,
        Err(e) => {
            // No backtrace: a malformed input journal is a data problem, and
            // the line number in the message is what actually helps.
            eprintln!("ems-slice: {e}");
            return ExitCode::from(EXIT_INPUT_ERROR);
        }
    };

    let mut ids = DeterministicIds::new(args.seed);
    let output = runner::run(&events, &mut ids);

    if let Err(e) = write_journal(&args.output, &output) {
        eprintln!("ems-slice: {e}");
        return ExitCode::from(EXIT_INPUT_ERROR);
    }
    ExitCode::from(EXIT_OK)
}

/// `Ok(None)` means `--help` was asked for: usage, but not an error the caller
/// needs to explain.
fn parse_args(command_line: &[String]) -> Result<Option<Args>, String> {
    let mut input: Option<PathBuf> = None;
    let mut output: Option<PathBuf> = None;
    let mut seed: u64 = 0;

    let mut i = 0;
    while i < command_line.len() {
        match command_line[i].as_str() {
            "--input" => {
                i += 1;
                let value = command_line.get(i).ok_or("--input requires a path")?;
                input = Some(PathBuf::from(value));
            }
            "--output" => {
                i += 1;
                let value = command_line.get(i).ok_or("--output requires a path")?;
                output = Some(PathBuf::from(value));
            }
            "--seed" => {
                i += 1;
                let value = command_line.get(i).ok_or("--seed requires a number")?;
                if value.starts_with('-') {
                    return Err(format!("--seed must not be negative: {value}"));
                }
                seed = value
                    .parse::<u64>()
                    .map_err(|_| format!("--seed is not a number: {value}"))?;
            }
            "--help" | "-h" => {
                eprintln!("{USAGE}");
                return Ok(None);
            }
            other => return Err(format!("unknown argument: {other}")),
        }
        i += 1;
    }

    let input = input.ok_or("--input is required")?;
    let output = output.ok_or("--output is required")?;
    Ok(Some(Args {
        input,
        output,
        seed,
    }))
}

#[cfg(test)]
#[allow(clippy::expect_used, clippy::unwrap_used, clippy::panic)]
mod tests {
    use super::parse_args;

    fn argv(args: &[&str]) -> Vec<String> {
        args.iter().map(|s| (*s).to_owned()).collect()
    }

    #[test]
    fn missing_input_is_an_error() {
        let err = parse_args(&argv(&["--output", "o"])).unwrap_err();
        assert!(err.contains("--input is required"), "{err}");
    }

    #[test]
    fn missing_output_is_an_error() {
        let err = parse_args(&argv(&["--input", "i"])).unwrap_err();
        assert!(err.contains("--output is required"), "{err}");
    }

    #[test]
    fn unknown_argument_is_an_error() {
        let err = parse_args(&argv(&["--wat"])).unwrap_err();
        assert!(err.contains("unknown argument"), "{err}");
    }

    #[test]
    fn negative_seed_is_an_error() {
        let err =
            parse_args(&argv(&["--input", "i", "--output", "o", "--seed", "-1"])).unwrap_err();
        assert!(err.contains("must not be negative"), "{err}");
    }

    #[test]
    fn non_numeric_seed_is_an_error() {
        let err =
            parse_args(&argv(&["--input", "i", "--output", "o", "--seed", "abc"])).unwrap_err();
        assert!(err.contains("not a number"), "{err}");
    }

    #[test]
    fn default_seed_is_zero() {
        let args = parse_args(&argv(&["--input", "i", "--output", "o"]))
            .unwrap()
            .unwrap();
        assert_eq!(args.seed, 0);
    }

    #[test]
    fn dangling_flag_value_is_an_error() {
        assert!(parse_args(&argv(&["--input"])).is_err());
    }
}
