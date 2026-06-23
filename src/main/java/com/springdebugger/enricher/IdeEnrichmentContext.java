package com.springdebugger.enricher;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The IDE-backed {@link EnrichmentContext}. This is the thin adapter the advisor asked for:
 * it does the PSI lookups and HTTP, then hands plain {@link ClassFacts} to the pure enrichers.
 * All PSI access runs inside a read action. HTTP (Actuator) is implemented by the M9 context;
 * here it returns empty.
 */
public final class IdeEnrichmentContext implements EnrichmentContext {

    /** Actuator probes must be quick; a slow or absent endpoint should not stall diagnosis. */
    private static final Duration HTTP_TIMEOUT = Duration.ofMillis(800);

    private final Project project;
    /** The detected HTTP port of the running app, or -1 when unknown (no Actuator probing then). */
    private final int appPort;

    public IdeEnrichmentContext(Project project) {
        this(project, -1);
    }

    public IdeEnrichmentContext(Project project, int appPort) {
        this.project = project;
        this.appPort = appPort;
    }

    @Override
    public Optional<ClassFacts> findClass(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return ReadAction.compute(() -> {
            PsiClass psiClass = resolve(name);
            return psiClass == null ? Optional.<ClassFacts>empty() : Optional.of(toFacts(psiClass));
        });
    }

    @Override
    public List<String> springBootApplicationPackages() {
        return ReadAction.compute(() -> {
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            PsiClass annotation = JavaPsiFacade.getInstance(project)
                    .findClass("org.springframework.boot.autoconfigure.SpringBootApplication",
                            GlobalSearchScope.allScope(project));
            if (annotation == null) return List.<String>of();

            Set<String> packages = new HashSet<>();
            for (PsiClass main : AnnotatedElementsSearch.searchPsiClasses(annotation, scope).findAll()) {
                String qn = main.getQualifiedName();
                if (qn != null) {
                    int dot = qn.lastIndexOf('.');
                    packages.add(dot >= 0 ? qn.substring(0, dot) : "");
                }
            }
            return new ArrayList<>(packages);
        });
    }

    @Override
    public Optional<String> httpGet(String path) {
        if (appPort <= 0) return Optional.empty();
        String base = path.startsWith("http") ? path : "http://localhost:" + appPort + path;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) return Optional.of(response.body());
            return Optional.empty();
        } catch (Exception e) {
            // App not reachable, no Actuator, or timed out: enrichment simply does not fire.
            return Optional.empty();
        }
    }

    private PsiClass resolve(String name) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        if (name.contains(".")) {
            PsiClass byFqn = facade.findClass(name, scope);
            if (byFqn != null) return byFqn;
        }
        String simple = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        PsiClass[] matches = PsiShortNamesCache.getInstance(project).getClassesByName(simple, scope);
        return matches.length > 0 ? matches[0] : null;
    }

    private ClassFacts toFacts(PsiClass psiClass) {
        String qn = psiClass.getQualifiedName();
        String pkg = "";
        if (qn != null) {
            int dot = qn.lastIndexOf('.');
            pkg = dot >= 0 ? qn.substring(0, dot) : "";
        }

        Set<String> annotations = new HashSet<>();
        for (PsiAnnotation a : psiClass.getAnnotations()) {
            String aqn = a.getQualifiedName();
            if (aqn != null) {
                int dot = aqn.lastIndexOf('.');
                annotations.add(dot >= 0 ? aqn.substring(dot + 1) : aqn);
            }
        }

        boolean inProjectSource = false;
        String fileName = null;
        PsiFile file = psiClass.getContainingFile();
        if (file != null) {
            VirtualFile vFile = file.getVirtualFile();
            if (vFile != null) {
                fileName = vFile.getName();
                inProjectSource = ProjectFileIndex.getInstance(project).isInSourceContent(vFile);
            }
        }

        return new ClassFacts(qn, pkg, psiClass.isInterface(), annotations,
                hasNoArgCtor(psiClass), inProjectSource, fileName);
    }

    private boolean hasNoArgCtor(PsiClass psiClass) {
        PsiMethod[] ctors = psiClass.getConstructors();
        if (ctors.length == 0) return true; // implicit default constructor
        for (PsiMethod ctor : ctors) {
            if (ctor.getParameterList().isEmpty()) return true;
        }
        return false;
    }
}
