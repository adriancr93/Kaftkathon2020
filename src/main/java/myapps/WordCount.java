package myapps;

import io.confluent.developer.avro.ActingEvent;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class WordCount{

    public Properties buildStreamsProperties(Properties envProps) {
        Properties props = new Properties();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("Stream-Kafka"));
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("localhot:9092"));
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty("schema.registry.url"));

        /*props.put(StreamsConfig.APPLICATION_ID_CONFIG, "Stream-Kafka");
        props.put(StreamsConfig.CLIENT_ID_CONFIG, "Stream-Kafka-Client");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 3);
        props.put(StreamsConfig.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        props.put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"UWHNZRVDZQO72PYC\" password=\"rb/NnyK2emH5nZsOc7h3a5xkTRgvmziMZBYh1gHRPbiM5lh0a1L5n39neJWNPlke\";");
        props.put(SslConfigs.DEFAULT_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "https");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 10 * 1000);
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        props.put(StreamsConfig.STATE_DIR_CONFIG, TestUtils.tempDirectory().getAbsolutePath());
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, props.getProperty("schema.registry.url"));

       props.put("producer.confluent.batch.expiry.ms", 920000000);
        props.put("zookeerper.connect", "localhost:2181");
        props.put("group.id", "test-group");
        props.put("schema.registry.url", "https://psrc-4rw99.us-central1.gcp.confluent.cloud");
        props.put("basic.auth.credentials.source", "USER_INFO");
        props.put("schema.registry.basic.auth.user.info", "FCFTOSXJOTAMX57N:nCTOUD" + "/" +"jxEY3cyv7msFe3gXxWY4jqUxa7ELWV8eDReZaZHPKv/gB0xCSQiX6OTMh");*/

        return props;
    }

    public Topology buildTopology(Properties envProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = envProps.getProperty("input.topic.name");

        KStream<String, ActingEvent>[] branches = builder.<String, ActingEvent>stream(inputTopic)
                .branch((key, appearance) -> "drama".equals(appearance.getGenre()),
                        (key, appearance) -> "fantasy".equals(appearance.getGenre()),
                        (key, appearance) -> true);

        branches[0].to(envProps.getProperty("output.drama.topic.name"));
        branches[1].to(envProps.getProperty("output.fantasy.topic.name"));
        branches[2].to(envProps.getProperty("output.other.topic.name"));

        return builder.build();
    }

    public void createTopics(Properties envProps) {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", envProps.getProperty("bootstrap.servers"));
        AdminClient client = AdminClient.create(config);

        List<NewTopic> topics = new ArrayList<>();

        topics.add(new NewTopic(
                envProps.getProperty("input.topic.name"),
                Integer.parseInt(envProps.getProperty("input.topic.partitions")),
                Short.parseShort(envProps.getProperty("input.topic.replication.factor"))));

        topics.add(new NewTopic(
                envProps.getProperty("output.drama.topic.name"),
                Integer.parseInt(envProps.getProperty("output.drama.topic.partitions")),
                Short.parseShort(envProps.getProperty("output.drama.topic.replication.factor"))));

        topics.add(new NewTopic(
                envProps.getProperty("output.fantasy.topic.name"),
                Integer.parseInt(envProps.getProperty("output.fantasy.topic.partitions")),
                Short.parseShort(envProps.getProperty("output.fantasy.topic.replication.factor"))));

        topics.add(new NewTopic(
                envProps.getProperty("output.other.topic.name"),
                Integer.parseInt(envProps.getProperty("output.other.topic.partitions")),
                Short.parseShort(envProps.getProperty("output.other.topic.replication.factor"))));

        client.createTopics(topics);
        client.close();
    }

    public Properties loadEnvProperties(String fileName) throws IOException {
        Properties envProps = new Properties();
        FileInputStream input = new FileInputStream(fileName);
        envProps.load(input);
        input.close();

        return envProps;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        WordCount ss = new WordCount();
        Properties envProps = ss.loadEnvProperties(args[0]);
        Properties streamProps = ss.buildStreamsProperties(envProps);
        Topology topology = ss.buildTopology(envProps);

        ss.createTopics(envProps);

        final KafkaStreams streams = new KafkaStreams(topology, streamProps);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}