package ru.itmo.vkr.channels;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import ru.itmo.vkr.channels.model.ChannelModel;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Command line entry point: writes the channel model of a system as JSON.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        Path root = null;
        Path gatewayConfig = null;
        Path out = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--gateway-config" -> gatewayConfig = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "-h", "--help" -> {
                    usage();
                    return;
                }
                default -> root = Path.of(args[i]);
            }
        }
        if (root == null) {
            usage();
            System.exit(2);
        }

        ChannelModel model = new ChannelExtractor(root, gatewayConfig).extract();
        ObjectWriter writer = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
                .writerWithDefaultPrettyPrinter();
        if (out == null) {
            writer.writeValue(System.out, model);
            System.out.println();
        } else {
            writer.writeValue(out.toFile(), model);
        }
        System.err.printf("services=%d endpoints=%d httpClients=%d publishers=%d bindings=%d listeners=%d "
                        + "channels=%d unresolved=%d warnings=%d%n",
                model.services().size(), model.endpoints().size(), model.httpClients().size(),
                model.publishers().size(), model.bindings().size(), model.listeners().size(),
                model.channels().size(), model.unresolved().size(), model.warnings().size());
    }

    private static void usage() {
        System.err.println("""
                Usage: channel-extractor <root> [--gateway-config <api-gateway.yml>] [--out <model.json>]

                  <root>             directory containing the service modules (src/main/java)
                  --gateway-config   Spring Cloud Gateway routes; default: first api-gateway.yml under <root>
                  --out              output file; default: standard output""");
    }
}
