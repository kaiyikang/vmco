package com.reviewm.adapter.in.cli;

import com.reviewm.adapter.out.output.ClipboardOutputAdapter;
import com.reviewm.adapter.out.output.ConsoleOutputAdapter;
import com.reviewm.adapter.out.output.FileOutputAdapter;
import com.reviewm.application.command.PackageReviewPromptCommand;
import com.reviewm.application.port.in.PackageReviewPromptUseCase;
import com.reviewm.application.port.out.PromptOutputPort;
import com.reviewm.application.port.out.RepositoryRootPort;
import com.reviewm.domain.model.PromptPackage;
import com.reviewm.domain.model.PromptProfile;
import com.reviewm.domain.model.ReviewFocus;
import com.reviewm.domain.policy.ContextBudgetPolicy;
import com.reviewm.shared.ReviewmException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ReviewmCommand {
    private final PackageReviewPromptUseCase packageReviewPromptUseCase;
    private final RepositoryRootPort repositoryRootPort;

    public ReviewmCommand(
        PackageReviewPromptUseCase packageReviewPromptUseCase,
        RepositoryRootPort repositoryRootPort
    ) {
        this.packageReviewPromptUseCase = packageReviewPromptUseCase;
        this.repositoryRootPort = repositoryRootPort;
    }

    public int execute(String[] args) {
        try {
            CliOptions options = CliOptions.parse(args);
            if (options.showHelp) {
                printHelp();
                return 0;
            }
            if ("review".equals(options.command)) {
                System.err.println("reviewm review is reserved for direct LLM clients. Use reviewm or reviewm prompt in this version.");
                return 2;
            }

            Path repositoryRoot = repositoryRootPort.resolveRepositoryRoot(Path.of("").toAbsolutePath());
            System.err.println("reviewm: using repository " + repositoryRoot);
            PromptProfile profile = new PromptProfile(
                options.templateName,
                options.language,
                options.focuses,
                ContextBudgetPolicy.normalize(options.maxContextChars),
                options.includeJavaContext,
                options.includeCallers
            );
            PromptPackage promptPackage = packageReviewPromptUseCase.packagePrompt(
                new PackageReviewPromptCommand(
                    repositoryRoot,
                    options.baseBranch,
                    options.currentBranch,
                    profile
                )
            );

            PromptOutputPort output = outputPort(options);
            System.err.println("reviewm: writing prompt to " + outputDescription(options) + "...");
            output.write(promptPackage.renderedPrompt());
            printSummary(options, promptPackage);
            return 0;
        } catch (IllegalArgumentException | ReviewmException e) {
            System.err.println("reviewm: " + e.getMessage());
            System.err.println("Run reviewm --help for usage.");
            return 2;
        }
    }

    private String outputDescription(CliOptions options) {
        return "file".equals(options.outputTarget) ? options.outputFile : options.outputTarget;
    }

    private PromptOutputPort outputPort(CliOptions options) {
        return switch (options.outputTarget) {
            case "clipboard" -> new ClipboardOutputAdapter();
            case "console" -> new ConsoleOutputAdapter(System.out);
            case "file" -> {
                if (options.outputFile == null || options.outputFile.isBlank()) {
                    throw new IllegalArgumentException("--output file requires --file <path>.");
                }
                yield new FileOutputAdapter(Path.of(options.outputFile));
            }
            default -> throw new IllegalArgumentException("Unknown output target: " + options.outputTarget);
        };
    }

    private void printSummary(CliOptions options, PromptPackage promptPackage) {
        if ("console".equals(options.outputTarget)) {
            return;
        }
        String target = "file".equals(options.outputTarget) ? options.outputFile : "clipboard";
        System.err.println("reviewm: prompt written to " + target
            + " (" + promptPackage.renderedPrompt().length() + " chars, "
            + promptPackage.changedFiles().size() + " files, "
            + promptPackage.contexts().size() + " contexts).");
        for (String warning : promptPackage.warnings()) {
            System.err.println("reviewm warning: " + warning);
        }
    }

    private void printHelp() {
        System.out.println("""
            Usage:
              reviewm [options]
              reviewm prompt [options]

            Default:
              Fetch origin, compare latest origin/main or origin/master with the
              current branch HEAD, render a review prompt, and copy it to the clipboard.
              Uncommitted working tree changes are ignored by default.

            Options:
              --copy                         Copy prompt to clipboard (default)
              --console                      Print prompt to stdout
              --output <clipboard|console|file>
              --file <path>                  Required when --output file is used
              --base <branch>                Base branch/ref, default: auto
              --current <branch>             Current branch/ref, default: current HEAD branch
              --template <name>              Prompt template, default: default-review
              --language <language>          Review output language, default: zh-CN
              --focus <a,b,c>                Review focus list
              --max-context-chars <n>        Java context budget, default: 16000
              --no-context                   Do not include Java method/class context
              --include-callers              Reserve caller context request; warns in v0.1
              -h, --help                     Show help

            Focus values:
              correctness, regression, tests, null-safety, concurrency,
              performance, security, api-compatibility
            """);
    }

    private static final class CliOptions {
        private String command = "prompt";
        private boolean showHelp;
        private String outputTarget = "clipboard";
        private String outputFile;
        private String baseBranch = "auto";
        private String currentBranch;
        private String templateName = "default-review";
        private String language = "zh-CN";
        private List<ReviewFocus> focuses = List.of(
            ReviewFocus.CORRECTNESS,
            ReviewFocus.REGRESSION,
            ReviewFocus.TESTS,
            ReviewFocus.NULL_SAFETY
        );
        private int maxContextChars = ContextBudgetPolicy.defaultContextChars();
        private boolean includeJavaContext = true;
        private boolean includeCallers;

        private static CliOptions parse(String[] args) {
            CliOptions options = new CliOptions();
            List<String> tokens = new ArrayList<>(Arrays.asList(args));
            if (!tokens.isEmpty()) {
                String first = tokens.get(0).toLowerCase(Locale.ROOT);
                if ("prompt".equals(first) || "review".equals(first)) {
                    options.command = first;
                    tokens.remove(0);
                }
            }

            for (int index = 0; index < tokens.size(); index++) {
                String token = tokens.get(index);
                switch (token) {
                    case "-h", "--help" -> options.showHelp = true;
                    case "--copy" -> options.outputTarget = "clipboard";
                    case "--console" -> options.outputTarget = "console";
                    case "--output" -> options.outputTarget = requireValue(tokens, ++index, "--output");
                    case "--file" -> options.outputFile = requireValue(tokens, ++index, "--file");
                    case "--base" -> options.baseBranch = requireValue(tokens, ++index, "--base");
                    case "--current" -> options.currentBranch = requireValue(tokens, ++index, "--current");
                    case "--template" -> options.templateName = requireValue(tokens, ++index, "--template");
                    case "--language" -> options.language = requireValue(tokens, ++index, "--language");
                    case "--focus" -> options.focuses = parseFocuses(requireValue(tokens, ++index, "--focus"));
                    case "--max-context-chars" -> options.maxContextChars = Integer.parseInt(
                        requireValue(tokens, ++index, "--max-context-chars")
                    );
                    case "--no-context" -> options.includeJavaContext = false;
                    case "--include-callers" -> options.includeCallers = true;
                    default -> throw new IllegalArgumentException("Unknown argument: " + token);
                }
            }
            options.outputTarget = options.outputTarget.toLowerCase(Locale.ROOT);
            return options;
        }

        private static String requireValue(List<String> tokens, int index, String option) {
            if (index >= tokens.size()) {
                throw new IllegalArgumentException(option + " requires a value.");
            }
            String value = tokens.get(index);
            if (value.startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value.");
            }
            return value;
        }

        private static List<ReviewFocus> parseFocuses(String value) {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            List<ReviewFocus> focuses = new ArrayList<>();
            for (String part : value.split(",")) {
                focuses.add(ReviewFocus.fromCliName(part));
            }
            return List.copyOf(focuses);
        }
    }
}
