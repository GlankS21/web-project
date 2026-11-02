package vkr;

import micans.MicansMain;
import micans.plugin.spring.MicroserviceHolder;
import pascal.taie.util.collection.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Same pipeline as {@link MicansMain}, but passes a YAML taint config to Tai-e
 * (MicansMain declares --taint-config without ever using it).
 *
 * <p>Usage: {@code MicansTaintMain <service> <appPaths> <libPaths> <taintConfig> <outputDir> [cs]},
 * where paths are comma-separated.
 */
public final class MicansTaintMain {

    private MicansTaintMain() {
    }

    public static void main(String[] argv) {
        String service = argv[0];
        List<String> app = Arrays.asList(argv[1].split(","));
        List<String> lib = Arrays.asList(argv[2].split(","));
        String taintConfig = argv[3];
        String outputDir = argv[4];
        String cs = argv.length > 5 ? argv[5] : "ci";

        MicroserviceHolder.clear();
        List<MicroserviceHolder> services = List.of(new MicroserviceHolder(service, app, lib));
        Pair<String, String> paths = MicroserviceHolder.getRunningClassPaths(services);
        // also taints String fields of web-endpoint DTO parameters (AppFirstParamProvider)
        MicansMain.taint = true;

        List<String> args = new ArrayList<>(List.of(
                "-java", "8", "-ap", "--pre-build-ir",
                "--output-dir", outputDir,
                "-acp", paths.first(),
                "-cp", paths.second(),
                "-a", """
                      pta=
                      implicit-entries:false;
                      distinguish-string-constants:app;
                      only-app:false;
                      handle-invokedynamic:true;
                      reflection-inference:null;
                      cs:%s;
                      taint-config:%s;
                      taint-config-providers:[];
                      plugins:[micans.plugin.spring.SpringAnalysis,
                               micans.plugin.rpc.RpcAnalysis,
                               micans.plugin.mbc.MbcAnalysis,
                               vkr.plugin.SpringDataModel,
                               vkr.plugin.ContainerTaint,
                               vkr.plugin.JdkTaintBarrier,
                               vkr.plugin.ListenerEntryPoints,
                               vkr.plugin.DebugDump];
                      time-limit:3600;
                      """.formatted(cs, taintConfig.replace('\\', '/'))));
        int status = 0;
        try {
            pascal.taie.Main.main(args.toArray(new String[0]));
            MicroserviceHolder.reportAllStatistics();
        } catch (Throwable t) {
            t.printStackTrace();
            status = 1;
        }
        // Tai-e leaves non-daemon threads behind; without this a failed run never exits
        System.exit(status);
    }
}
