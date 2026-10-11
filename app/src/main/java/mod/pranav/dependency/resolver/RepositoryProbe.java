package mod.pranav.dependency.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class RepositoryProbe {

    private static final int POM_LIMIT_BYTES = 4 * 1024 * 1024;
    private static final int POM_ATTEMPTS = 2;

    private RepositoryProbe() {
    }

    public static final class Repo {
        public final String name;
        public final String url;

        public Repo(@NonNull String name, @NonNull String url) {
            this.name = name;
            this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
    }

    public static final class Attempt {
        public final Repo repo;
        public final String url;
        public final int code;
        public final String detail;

        Attempt(Repo repo, String url, int code, String detail) {
            this.repo = repo;
            this.url = url;
            this.code = code;
            this.detail = detail;
        }

        boolean isNetworkFailure() {
            return code < 0;
        }
    }

    public enum Kind {POM, MODULE, FILE}

    public static final class Result {
        public final int foundIndex;
        @Nullable
        public final String foundUrl;
        @Nullable
        public final Kind foundKind;
        public final List<Attempt> attempts;
        public final boolean cancelled;

        Result(int foundIndex, @Nullable String foundUrl, @Nullable Kind foundKind, List<Attempt> attempts, boolean cancelled) {
            this.foundIndex = foundIndex;
            this.foundUrl = foundUrl;
            this.foundKind = foundKind;
            this.attempts = attempts;
            this.cancelled = cancelled;
        }

        public boolean found() {
            return foundIndex >= 0;
        }

        public boolean allNetworkFailures() {
            if (attempts.isEmpty()) return false;
            for (Attempt attempt : attempts) {
                if (!attempt.isNetworkFailure()) return false;
            }
            return true;
        }

        @NonNull
        public String explain(@NonNull String coordinate) {
            StringBuilder out = new StringBuilder();
            if (allNetworkFailures()) {
                out.append("No repository could be reached while looking for ").append(coordinate).append(". Check the internet connection.");
            } else {
                out.append(coordinate).append(" was not found in any configured repository (checked POM, Gradle module metadata and the .aar/.jar file in each).");
            }
            String currentRepo = null;
            StringBuilder line = new StringBuilder();
            for (Attempt attempt : attempts) {
                if (!attempt.repo.name.equals(currentRepo)) {
                    if (currentRepo != null) out.append("\n- ").append(currentRepo).append(": ").append(line);
                    currentRepo = attempt.repo.name;
                    line.setLength(0);
                } else {
                    line.append("; ");
                }
                line.append(attempt.detail);
            }
            if (currentRepo != null) out.append("\n- ").append(currentRepo).append(": ").append(line);
            return out.toString();
        }
    }

    @NonNull
    public static String pomUrl(@NonNull String repoUrl, @NonNull String group, @NonNull String artifact, @NonNull String version) {
        String base = repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;
        return base + "/" + group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".pom";
    }

    @NonNull
    public static String moduleUrl(@NonNull String repoUrl, @NonNull String group, @NonNull String artifact, @NonNull String version) {
        String base = repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;
        return base + "/" + group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".module";
    }

    @NonNull
    public static String fileUrl(@NonNull String repoUrl, @NonNull String group, @NonNull String artifact, @NonNull String version, @NonNull String extension) {
        String base = repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;
        return base + "/" + group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version + "." + extension;
    }

    @NonNull
    public static List<Integer> probeOrder(@NonNull List<Repo> repos) {
        List<Integer> order = new ArrayList<>();
        List<Integer> deferred = new ArrayList<>();
        for (int i = 0; i < repos.size(); i++) {
            if (repos.get(i).url.contains("jitpack.io")) deferred.add(i);
            else order.add(i);
        }
        order.addAll(deferred);
        return order;
    }

    @NonNull
    public static Result locate(@NonNull List<Repo> repos, @NonNull String group, @NonNull String artifact,
                                @NonNull String version, @Nullable BooleanSupplier cancelled) {
        List<Attempt> attempts = new ArrayList<>();
        for (int index : probeOrder(repos)) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                return new Result(-1, null, null, attempts, true);
            }
            Repo repo = repos.get(index);

            String pomUrl = pomUrl(repo.url, group, artifact, version);
            HttpFetcher.Response pom = HttpFetcher.getWithRetry(pomUrl, POM_LIMIT_BYTES, POM_ATTEMPTS, cancelled);
            if (pom.isSuccess() && !pom.body.trim().isEmpty()) {
                attempts.add(new Attempt(repo, pomUrl, pom.code, "POM found"));
                return new Result(index, pomUrl, Kind.POM, attempts, false);
            }
            attempts.add(new Attempt(repo, pomUrl, pom.isSuccess() ? 0 : pom.code, "pom: " + (pom.isSuccess() ? "empty response" : pom.describe())));
            if (pom.isNetworkFailure()) continue;

            String moduleUrl = moduleUrl(repo.url, group, artifact, version);
            HttpFetcher.Response module = HttpFetcher.getWithRetry(moduleUrl, POM_LIMIT_BYTES, POM_ATTEMPTS, cancelled);
            if (module.isSuccess() && !module.body.trim().isEmpty()) {
                attempts.add(new Attempt(repo, moduleUrl, module.code, "module metadata found (no POM)"));
                return new Result(index, moduleUrl, Kind.MODULE, attempts, false);
            }
            attempts.add(new Attempt(repo, moduleUrl, module.isSuccess() ? 0 : module.code, "module: " + (module.isSuccess() ? "empty response" : module.describe())));
            if (module.isNetworkFailure()) continue;

            for (String extension : new String[]{"aar", "jar"}) {
                String fileUrl = fileUrl(repo.url, group, artifact, version, extension);
                int status = HttpFetcher.statusOf(fileUrl);
                if (status >= 200 && status < 300) {
                    attempts.add(new Attempt(repo, fileUrl, status, extension + " file found (no POM)"));
                    return new Result(index, fileUrl, Kind.FILE, attempts, false);
                }
                attempts.add(new Attempt(repo, fileUrl, status, extension + ": " + (status < 0 ? "unreachable" : FailureFormatter.httpStatus(status))));
                if (status < 0) break;
            }
        }
        return new Result(-1, null, null, attempts, false);
    }
}
