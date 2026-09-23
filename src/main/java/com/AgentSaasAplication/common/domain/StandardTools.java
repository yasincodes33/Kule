package com.AgentSaasAplication.common.domain;

import java.util.List;
import java.util.Map;

public final class StandardTools {

    public static final ToolDefinition READ_FILE = new ToolDefinition(
            "read_file",
            "Proje dizini içindeki bir dosyanın içeriğini okur.",
            Map.of("path", new ToolDefinition.ParameterSchema("string", "Proje köküne göre göreli dosya yolu")),
            List.of("path"),
            DurationClass.FAST
    );

    public static final ToolDefinition WRITE_FILE = new ToolDefinition(
            "write_file",
            "Proje dizini içinde bir dosyaya içerik yazar (varsa üzerine yazar).",
            Map.of(
                    "path", new ToolDefinition.ParameterSchema("string", "Proje köküne göre göreli dosya yolu"),
                    "content", new ToolDefinition.ParameterSchema("string", "Dosyaya yazılacak tam içerik")
            ),
            List.of("path", "content"),
            DurationClass.MEDIUM
    );

    public static final ToolDefinition LIST_DIRECTORY = new ToolDefinition(
            "list_directory",
            "Proje dizini içinde bir klasörün içeriğini listeler.",
            Map.of("path", new ToolDefinition.ParameterSchema("string", "Proje köküne göre göreli klasör yolu (kök için boş bırakılabilir)")),
            List.of(),
            DurationClass.FAST
    );

    public static final ToolDefinition RUN_COMMAND = new ToolDefinition(
            "run_command",
            "Proje dizininde bir kabuk komutu çalıştırır ve çıktısını döner.",
            Map.of("command", new ToolDefinition.ParameterSchema("string", "Çalıştırılacak komut")),
            List.of("command"),
            DurationClass.SLOW
    );

    public static final ToolDefinition GIT_STATUS = new ToolDefinition(
            "git_status",
            "Git reposunun anlık durumunu (stage edilmiş/edilmemiş dosyalar, untracked dosyalar) gösterir.",
            Map.of(),
            List.of(),
            DurationClass.FAST
    );

    public static final ToolDefinition GIT_DIFF = new ToolDefinition(
            "git_diff",
            "Belirli bir dosyadaki veya tüm projedeki değişiklikleri (diff) gösterir.",
            Map.of("path", new ToolDefinition.ParameterSchema("string", "Değişiklikleri görmek istenen dosya yolu. Boş bırakılırsa tüm değişiklikleri gösterir.")),
            List.of(),
            DurationClass.FAST
    );

    public static final ToolDefinition GIT_LOG = new ToolDefinition(
            "git_log",
            "Git commit geçmişini gösterir.",
            Map.of("max_count", new ToolDefinition.ParameterSchema("integer", "Gösterilecek maksimum commit sayısı. Varsayılan: 10")),
            List.of(),
            DurationClass.FAST
    );

    public static final ToolDefinition GIT_BRANCH_LIST = new ToolDefinition(
            "git_branch_list",
            "Mevcut yerel ve uzak (remote) branch'leri listeler.",
            Map.of(),
            List.of(),
            DurationClass.FAST
    );

    public static final ToolDefinition GIT_ADD = new ToolDefinition(
            "git_add",
            "Değişiklikleri stage (index) alanına ekler.",
            Map.of("path", new ToolDefinition.ParameterSchema("string", "Stage'e eklenecek dosya/klasör yolu. Tüm değişiklikler için '.' (nokta) kullanılabilir.")),
            List.of("path"),
            DurationClass.MEDIUM
    );

    public static final ToolDefinition GIT_COMMIT = new ToolDefinition(
            "git_commit",
            "Stage alanındaki değişiklikleri bir mesajla commit eder.",
            Map.of("message", new ToolDefinition.ParameterSchema("string", "Commit mesajı")),
            List.of("message"),
            DurationClass.MEDIUM
    );

    public static final ToolDefinition GIT_PUSH = new ToolDefinition(
            "git_push",
            "Yerel commitleri uzak (remote) sunucuya gönderir.",
            Map.of(
                    "remote", new ToolDefinition.ParameterSchema("string", "Uzak sunucu adı, varsayılan: 'origin'"),
                    "branch", new ToolDefinition.ParameterSchema("string", "Gönderilecek branch, varsayılan mevcut branch"),
                    "force", new ToolDefinition.ParameterSchema("boolean", "Eğer true ise '--force' ile push eder (riskli işlem, onay gerektirir). Varsayılan: false")
            ),
            List.of(),
            DurationClass.SLOW
    );

    public static final ToolDefinition GIT_PULL = new ToolDefinition(
            "git_pull",
            "Uzak (remote) sunucudan güncellemeleri çeker ve mevcut branch'e entegre eder.",
            Map.of(
                    "remote", new ToolDefinition.ParameterSchema("string", "Uzak sunucu adı, varsayılan: 'origin'"),
                    "branch", new ToolDefinition.ParameterSchema("string", "Çekilecek branch, varsayılan mevcut branch")
            ),
            List.of(),
            DurationClass.SLOW
    );

    public static final ToolDefinition GIT_CHECKOUT = new ToolDefinition(
            "git_checkout",
            "Başka bir branch'e geçiş yapar veya belirli bir dosyayı (ör. çalışma alanındaki değişiklikleri iptal etmek için) önceki haline geri getirir.",
            Map.of("branch_or_path", new ToolDefinition.ParameterSchema("string", "Geçiş yapılacak branch adı veya geri yüklenecek dosya yolu")),
            List.of("branch_or_path"),
            DurationClass.MEDIUM
    );

    public static final ToolDefinition GIT_MERGE = new ToolDefinition(
            "git_merge",
            "Belirtilen branch'i mevcut branch'e merge eder.",
            Map.of("branch", new ToolDefinition.ParameterSchema("string", "Merge edilecek branch adı")),
            List.of("branch"),
            DurationClass.MEDIUM
    );

    public static final ToolDefinition GIT_CREATE_BRANCH = new ToolDefinition(
            "git_create_branch",
            "Yeni bir branch oluşturur ve opsiyonel olarak o branch'e geçer.",
            Map.of(
                    "branch_name", new ToolDefinition.ParameterSchema("string", "Oluşturulacak yeni branch'in adı"),
                    "checkout", new ToolDefinition.ParameterSchema("boolean", "Eğer true ise yeni branch oluşturulduktan sonra o branch'e geçiş (checkout) yapılır. Varsayılan: true")
            ),
            List.of("branch_name"),
            DurationClass.MEDIUM
    );

    public static final List<ToolDefinition> ALL = List.of(
            READ_FILE, WRITE_FILE, LIST_DIRECTORY, RUN_COMMAND,
            GIT_STATUS, GIT_DIFF, GIT_LOG, GIT_BRANCH_LIST,
            GIT_ADD, GIT_COMMIT, GIT_PUSH, GIT_PULL, GIT_CHECKOUT, GIT_MERGE, GIT_CREATE_BRANCH
    );

    /** Araç adından tanımı bulur — connector'lar zaman aşımını buradan okuyor. */
    public static java.util.Optional<ToolDefinition> byName(String name) {
        return ALL.stream().filter(t -> t.name().equals(name)).findFirst();
    }

    private StandardTools() {}
}