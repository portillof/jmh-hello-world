package com.example.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Naive vs optimized: bubble sort vs {@link Arrays#sort(int[])}
 * (dual-pivot quicksort in the JDK) on the same 100-element input.
 *
 * Nobody should be hand-rolling sorting code today — this exists purely
 * to show how JMH makes the gap measurable.
 *
 * Note the {@code Level.Invocation} setup: sorting mutates its input, so
 * each invocation gets a fresh unsorted copy. Otherwise every run after
 * the first would sort already-sorted data and lie to us.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Thread)
public class SortingBenchmark {

    private static final int SIZE = 100;

    private int[] source;
    private int[] working;

    @Setup(Level.Trial)
    public void generateData() {
        // Fixed seed: every fork and every benchmark sees the same input
        Random random = new Random(42);
        source = random.ints(SIZE).toArray();
    }

    @Setup(Level.Invocation)
    public void freshCopy() {
        working = Arrays.copyOf(source, source.length);
    }

    @Benchmark
    public int[] bubbleSort() {
        int[] array = working;
        for (int i = 0; i < array.length - 1; i++) {
            for (int j = 0; j < array.length - 1 - i; j++) {
                if (array[j] > array[j + 1]) {
                    int tmp = array[j];
                    array[j] = array[j + 1];
                    array[j + 1] = tmp;
                }
            }
        }
        return array;    // returning the result keeps dead-code elimination away
    }

    @Benchmark
    public int[] jdkSort() {
        int[] array = working;
        Arrays.sort(array);
        return array;
    }
}
