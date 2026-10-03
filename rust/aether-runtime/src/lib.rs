pub fn version() -> &'static str {
    "0.1.0"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn runtime_version_exists() {
        assert_eq!(version(), "0.1.0");
    }
}
