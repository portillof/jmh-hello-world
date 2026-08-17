# jmh-hello-world

A tiny, hands-on introduction to **JMH (Java Microbenchmark Harness)** — the OpenJDK tool for writing correct microbenchmarks on the JVM.

## What is microbenchmarking, and why do we need a harness?

Microbenchmarking means measuring the performance of a *small* piece of code — a method, a loop, a data-structure operation — as opposed to profiling a whole application. The problem: the JVM actively works against naive measurement:

- **JIT compilation & warmup** — code starts interpreted, then gets compiled (and re-compiled) as it gets hot. Measuring before warmup measures the interpreter, not your code.
- **Dead-code elimination** — if the JIT sees a result is never used, it may delete the computation entirely. Your benchmark then measures nothing, very fast.
- **Constant folding** — if inputs are compile-time predictable, the JIT may precompute results.
- **On-stack replacement, inlining, GC pauses, CPU frequency scaling…**

A `System.nanoTime()` loop gets all of this wrong. **JMH** is built by the same people who build the JVM (OpenJDK) and handles warmup, forking, statistical aggregation, and dead-code traps (`Blackhole`) for you. You declare *what* to measure with annotations; JMH generates the harness code at compile time.

### Core annotations (the ones in this repo)

| Annotation | What it does |
|---|---|
| `@Benchmark` | Marks a method as a benchmark |
| `@BenchmarkMode` | `Throughput` (ops/time), `AverageTime`, `SampleTime`, `SingleShotTime`, `All` |
| `@Warmup` / `@Measurement` | How many iterations to warm up (discarded) vs actually measure |
| `@Fork` | Runs benchmarks in fresh JVM child processes (isolates JIT profiles) |
| `@State` + `@Setup` | Holds input data; setup at trial / iteration / invocation level |
| `@Param` | Runs the same benchmark across several input values |
| `@OutputTimeUnit` | Unit for reported results |

## The benchmarks in this repo

1. **`AutoboxingBenchmark`** — sums numbers into a primitive `long` vs a boxed `Long`. The boxed accumulator forces unbox → add → box on every loop lap (values above 127 can't come from the `Long` cache, so each box is a fresh allocation). Question for the room: *is this still a thing in Java 21?* Run it and see. (Spoiler: yes — escape analysis helps in some shapes, but not accumulation loops.)
2. **`SortingBenchmark`** — bubble sort vs `Arrays.sort` (dual-pivot quicksort) on the same 100-element array. Nobody should hand-roll sorting today; this just makes the cost of naive code *visible*. It also demonstrates `@Setup(Level.Invocation)`: sorting mutates its input, so every invocation needs a fresh unsorted copy.

## How to run

Requires Java 17+ (built/tested with Java 21) and Maven.

```bash
# Build the self-contained benchmarks.jar (the standard JMH workflow)
mvn clean package

# Run everything (uses the @Warmup/@Measurement settings in the code)
java -jar target/benchmarks.jar

# Run a single benchmark class (regex match)
java -jar target/benchmarks.jar Autoboxing

# Quick demo run: 1 warmup, 1 measurement iteration, 1 fork (~1 min total)
java -jar target/benchmarks.jar -wi 1 -i 1 -f 1

# List available benchmarks / all CLI options
java -jar target/benchmarks.jar -l
java -jar target/benchmarks.jar -h
```

> ⚠️ The quick-run flags are for demos. For numbers you'd actually trust, use more iterations and ≥2 forks, on a quiet machine.

## Reports

JMH has **native report output** — no plugin needed:

```bash
# JSON (also: csv, scsv, latex, text)
java -jar target/benchmarks.jar -rf json -rff jmh-result.json
```

Drop the JSON file into [JMH Visualizer](https://jmh.morethan.io/) for interactive charts, or diff two result files across commits.

Sample console output from this repo (Java 21, Apple Silicon, quick-run settings — your numbers will differ, the *ratios* are the story):

```
Benchmark                         (iterations)   Mode  Cnt    Score   Error   Units
AutoboxingBenchmark.boxedSum             10000  thrpt        64.676          ops/ms
AutoboxingBenchmark.primitiveSum         10000  thrpt       296.505          ops/ms
SortingBenchmark.bubbleSort                N/A   avgt         3.176           us/op
SortingBenchmark.jdkSort                   N/A   avgt         0.970           us/op
```

So on Java 21: the primitive accumulator is **~4.6× faster** than the boxed one, and `Arrays.sort` beats bubble sort **~3.3×** even at a tiny 100 elements (the gap grows as O(n²) vs O(n log n) with size).

## JMH in CI/CD

Honest take first: **microbenchmarks in CI are hard**. Shared runners have noisy neighbours, frequency scaling, and varying hardware — absolute numbers are not comparable across runs. What *does* work:

1. **Run benchmarks as a separate, non-blocking pipeline stage** (nightly or on-demand via a label/manual trigger), not on every PR — they're slow by design.
2. **Emit machine-readable results** with `-rf json -rff results.json` and archive them as build artifacts.
3. **Compare relative, not absolute**: run baseline (main) and candidate (PR) benchmarks *in the same job on the same runner*, and fail only on large regressions (e.g. >20–30%), since run-to-run noise of a few percent is normal.
4. **Use dedicated/bare-metal runners** if you need trustworthy trend lines over time.

Minimal GitHub Actions example:

```yaml
name: benchmarks
on:
  workflow_dispatch:        # manual trigger — don't run on every push
  schedule:
    - cron: "0 3 * * 1"    # weekly, Monday 03:00

jobs:
  jmh:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21" }
      - run: mvn -B clean package
      - run: java -jar target/benchmarks.jar -rf json -rff jmh-result.json
      - uses: actions/upload-artifact@v4
        with: { name: jmh-results, path: jmh-result.json }
```

For automated regression gating, [github-action-benchmark](https://github.com/benchmark-action/github-action-benchmark) understands JMH's JSON format and can comment on PRs / fail on thresholds.

## Gotchas worth mentioning in the room

- **Always return (or `Blackhole.consume`) computed values** — otherwise dead-code elimination eats your benchmark.
- **`@Setup(Level.Invocation)` is a last resort** — it has timing overhead; we use it here only because sorting mutates its input.
- **Fork ≥ 1 always** — forking isolates each benchmark from the JIT profile pollution of the others.
- **Benchmark numbers from a laptop on battery/thermal throttle are fiction.** Plug in, close Chrome.

## Sources / further reading

- [Baeldung — Microbenchmarking with Java (JMH)](https://www.baeldung.com/java-microbenchmark-harness)
- [Learning JMH, Part 1: The Setup (Nitin Karthy)](https://nitin-karthy.medium.com/learning-jmh-part-1-the-setup-45e819fddae4)
- [Official JMH samples](https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples) — the best JMH documentation there is
- [JMH Visualizer](https://jmh.morethan.io/)
