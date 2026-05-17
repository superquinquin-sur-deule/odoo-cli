package org.hoohoot.odoo.command;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;

@Command(
        name = "update",
        description = "Met à jour le binaire vers la dernière release GitHub",
        mixinStandardHelpOptions = true
)
public class UpdateCommand implements Callable<Integer> {

    private static final String REPO = "superquinquin-sur-deule/odoo-cli";

    @Option(names = "--check", description = "Affiche la dernière version disponible sans installer")
    boolean check;

    @Inject
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String currentVersion;

    @Override
    public Integer call() {
        try {
            String tag = resolveLatestTag();
            if (tag == null) {
                System.err.println("Impossible de résoudre la dernière release de " + REPO);
                return 2;
            }
            String latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
            boolean upToDate = latestVersion.equals(currentVersion);

            System.out.println("Version actuelle : " + currentVersion);
            System.out.println("Dernière version disponible : " + tag);
            System.out.println(upToDate
                    ? "Vous êtes déjà sur la dernière version."
                    : "Une mise à jour est disponible (" + currentVersion + " → " + latestVersion + ").");

            if (check) {
                return 0;
            }

            if (upToDate) {
                return 0;
            }

            Path currentBinary = currentBinaryPath();
            String asset = "odoo-cli-" + latestVersion + "-" + detectPlatform();
            URI url = URI.create("https://github.com/" + REPO + "/releases/download/" + tag + "/" + asset);

            System.out.println("Téléchargement de " + asset + "...");
            Path tmp = Files.createTempFile(currentBinary.getParent(), "odoo-cli-", ".tmp");
            try (HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build()) {
                HttpResponse<Path> response = client.send(
                        HttpRequest.newBuilder(url).GET().build(),
                        HttpResponse.BodyHandlers.ofFile(tmp)
                );
                if (response.statusCode() != 200) {
                    Files.deleteIfExists(tmp);
                    System.err.println("Téléchargement échoué (HTTP " + response.statusCode() + ")");
                    return 3;
                }
            }

            tmp.toFile().setExecutable(true, true);
            Files.move(tmp, currentBinary,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Mis à jour vers " + tag + " (" + currentBinary + ")");
            return 0;

        } catch (Exception e) {
            System.err.println("Erreur : " + e.getMessage());
            return 1;
        }
    }

    private static String resolveLatestTag() throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()) {
            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(URI.create("https://github.com/" + REPO + "/releases/latest"))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.discarding()
            );
            String location = response.headers().firstValue("location").orElse(null);
            if (location == null) {
                return null;
            }
            int idx = location.lastIndexOf('/');
            String tag = idx >= 0 ? location.substring(idx + 1) : null;
            return (tag == null || tag.isBlank() || "releases".equals(tag)) ? null : tag;
        }
    }

    private static Path currentBinaryPath() throws IOException {
        Path procSelfExe = Path.of("/proc/self/exe");
        Path resolved;
        if (Files.exists(procSelfExe)) {
            resolved = procSelfExe.toRealPath();
        } else {
            String command = ProcessHandle.current().info().command()
                    .orElseThrow(() -> new IOException("Impossible de déterminer le chemin du binaire courant"));
            resolved = Path.of(command).toRealPath();
        }
        String name = resolved.getFileName().toString();
        if (name.equals("java") || resolved.toString().contains("/jdk") || resolved.toString().contains("/jre")) {
            throw new IOException("La commande update n'est utilisable qu'avec le binaire natif (détecté : " + resolved + ")");
        }
        return resolved;
    }

    private static String detectPlatform() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        String osTag;
        if (os.contains("linux")) {
            osTag = "linux";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osTag = "macos";
        } else {
            throw new IOException("OS non supporté pour la mise à jour : " + os);
        }

        String archTag;
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            archTag = "x86_64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            archTag = "aarch64";
        } else {
            throw new IOException("Architecture non supportée pour la mise à jour : " + arch);
        }

        return osTag + "-" + archTag;
    }
}
