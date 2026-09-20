# Changelog

All notable changes to **typesafe-java** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

First version — no released API yet, so nothing to describe changes against.

- Added `cli` module: an executable uber-jar for ad hoc checks against the API from a
  terminal, without writing Java. `--verbose`/`--timing` print the request id / response
  time to stderr; `--version` prints the jar's version and exits without calling the API.
  Not published as a library artifact.
- Removed `TypesafeClient.withDefaultToken()`: its name read as "a default token" rather
  than "the default token *file*". Use `TypesafeClient.builder(ApiToken.fromDefaultFile()).build()`.
