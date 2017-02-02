package com.outbrain.swinfra.metrics

import com.outbrain.swinfra.metrics.samples.SampleCreator
import com.outbrain.swinfra.metrics.samples.StaticLablesSampleCreator
import spock.lang.Specification

import static com.outbrain.swinfra.metrics.Histogram.HistogramBuilder
import static io.prometheus.client.Collector.MetricFamilySamples
import static io.prometheus.client.Collector.MetricFamilySamples.Sample
import static io.prometheus.client.Collector.Type.HISTOGRAM

class HistogramTest extends Specification {

    private static final SampleCreator sampleCreator = new StaticLablesSampleCreator([:])
    private static final String NAME = "myHisto"
    private static final String HELP = "HELP"
    private static final String LAST_BUCKET_VALUE = "+Inf"

    def 'Histogram should return the correct type'() {
        given:
            final Histogram histogram = new HistogramBuilder(NAME, HELP).build()

        expect:
            histogram.getType() == HISTOGRAM
    }

    def 'Newly created histogram, with no specific buckets, should contain a single bucket'() {
        given:
            final List<Sample> samples = generateHistogramSamples(["+Inf": 0], 0)
            final MetricFamilySamples metricFamilySamples = new MetricFamilySamples(NAME, HISTOGRAM, HELP, samples)

            final Histogram histogram = new HistogramBuilder(NAME, HELP).build()

        expect:
            histogram.getSample(sampleCreator) == metricFamilySamples
    }

    def 'Histogram with defined buckets should return samples relevant for these buckets'() {
        given:
            final List<Sample> samples = generateHistogramSamples(
                ["1.0": 1, "10.0": 2, "100.0": 2, "+Inf": 1],
                1 + 5 + 5 + 50 + 50 + 150)
            final MetricFamilySamples metricFamilySamples = new MetricFamilySamples(NAME, HISTOGRAM, HELP, samples)

            final Histogram histogram = new HistogramBuilder(NAME, HELP).withBuckets(1, 10, 100).build()

        when:
            histogram.observe(1)
            histogram.observe(5)
            histogram.observe(5)
            histogram.observe(50)
            histogram.observe(50)
            histogram.observe(150)

        then:
            histogram.getSample(sampleCreator) == metricFamilySamples
    }

    def 'Histogram with defined buckets and labels should return correct samples with correct lables'() {
        given:
            final String labelName = "lab1"
            final List<String> labelValues = ["val1", "val2", "val3"]
            final List<Sample> samplesWithoutLabels = generateHistogramSamples(
                ["1.0": 1, "10.0": 2, "100.0": 2, "+Inf": 1],
                1 + 5 + 5 + 50 + 50 + 150)

            final List<Sample> samples = labelValues.collect {
                addLabelsToSample(samplesWithoutLabels, [labelName], [it])
            }.flatten()
            final MetricFamilySamples metricFamilySamples = new MetricFamilySamples(NAME, HISTOGRAM, HELP, samples)

            final Histogram histogram = new HistogramBuilder(NAME, HELP)
                .withLabels(labelName)
                .withBuckets(1, 10, 100)
                .build()

        when:
            labelValues.each {
                histogram.observe(1, it)
                histogram.observe(5, it)
                histogram.observe(5, it)
                histogram.observe(50, it)
                histogram.observe(50, it)
                histogram.observe(150, it)
            }

        then:
            final MetricFamilySamples actualMetricFamilySamples = histogram.getSample(sampleCreator)
            actualMetricFamilySamples.samples.sort() == metricFamilySamples.samples.sort()
            actualMetricFamilySamples.name == metricFamilySamples.name
            actualMetricFamilySamples.help == metricFamilySamples.help
            actualMetricFamilySamples.type == metricFamilySamples.type
    }

    /**
     *
     * @param eventsForBucket eventsForBucket - maps [bucket: numOfEvents]
     * For example, generateHistogramSamples(["1":1, "10":2, "100":3, "+Inf":4])
     * Means there was one event with value "1" or less, another one with the value between "1" (exclusive) and "10" (inclusive), another between
     * "10" and "100" and another that's more than "100"
     * The following observations correspond to this example:
     * histo.observe(1)
     * histo.observe(5)
     * histo.observe(50)
     * histo.observe(150)
     * @param sumOfSamples The sum of all the samples
     * @return
     */
    private static List<Sample> generateHistogramSamples(final Map<String, Long> eventsForBucket,
                                                         final double sumOfSamples) {
        long totalEvents = 0
        final List<Sample> result = []
        for (final Map.Entry<String, Long> bucketEvents : eventsForBucket) {
            final String bucket = bucketEvents.getKey()
            final long events = bucketEvents.getValue()
            totalEvents += events
            result.add(new Sample("${NAME}_bucket", ["le"] as List<String>, [bucket] as List<String>, totalEvents))
        }
        result.add(new Sample("${NAME}_count", [] as List<String>, [] as List<String>, totalEvents))
        result.add(new Sample("${NAME}_sum", [] as List<String>, [] as List<String>, sumOfSamples))
        return result
    }

    private static List<Sample> addLabelsToSample(final List<Sample> origin,
                                                  final List<String> labelNames,
                                                  final List<String> labelValues) {
        return origin.collect {
            final Sample newSample =
                new Sample(
                    it.name,
                    (labelNames + it.labelNames) as List<String>,
                    (labelValues + it.labelValues) as List<String>,
                    it.value)
            return newSample
        }
    }

}