#!/usr/bin/env bash
# Lists every REST endpoint mapping across big4's backend and investments-service, straight from
# source. Used by the `endpoints` skill for fast existence/path lookups -- re-run this, don't trust
# a cached table, since it drifts the moment a controller changes.

grep -rn "@RequestMapping\|@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping\|@PatchMapping" \
  backend/src/main/java investments-service/src/main/java
