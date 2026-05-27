# VMCO

Sprachen: [English](README.md) | Deutsch | [中文](README.zh-CN.md)

VMCO erstellt Prompt-Dateien für IntelliJ Copilot Agent, wenn das IDE-Plugin
Repository-, Diff- oder Ticket-Kontext nicht selbst sammeln kann.

## PR Reviewer

Rufe das VMCO-Skript aus dem Root-Verzeichnis des Projekts auf, das analysiert werden soll:

```bash
/path/to/vmco/bin/create-llm-review-diffs.sh [base-branch] [output-dir]
```

Standardwerte:

- `base-branch`: `master`
- `output-dir`: `.llm`
- Ausgabedatei: `<output-dir>/review-instruction.md`

Wechsle danach IntelliJ Copilot in den Agent-Modus und lasse Copilot zuerst die erzeugte Datei lesen.

## Context For JIRA

Setze `JIRA_TOKEN` und `JIRA_URL_TEMPLATE` in der Umgebung oder in der `.env`
Datei im Ziel-Repository. Die URL-Vorlage muss `{ticket}` enthalten.

```bash
python3 /path/to/vmco/bin/context-for-jira.py <ticket-id>
```

Das Skript schreibt `.llm/context-for-jira-{ticket}-{timestamp}.md`.

## Designprinzipien

- Skripte sammeln Fakten außerhalb der IDE; Copilot analysiert im Repository.
- Generierte Prompts sind einzelne Dateien, nachvollziehbar und klar begrenzt.
- Große Diffs werden standardmäßig referenziert statt eingebettet.
- Versionierte Prompt-Vorlagen werden genutzt, wenn stabile Review-Historie nötig ist.
