# VMCO

Sprachen: [English](README.md) | Deutsch | [中文](README.zh-CN.md)

VMCO erzeugt Kontext-Prompts, die IntelliJ Copilot lesen kann.

Wenn das Copilot-Plugin externe Werkzeuge nicht direkt aufrufen kann, sammelt VMCO Repository-, Branch-Diff- oder Ticket-Informationen außerhalb der IDE und schreibt eine Prompt-Datei für Copilot Agent.

## Schnellstart

Rufe das VMCO-Skript aus dem Root-Verzeichnis des Projekts auf, das analysiert werden soll:

```bash
/path/to/vmco/bin/create-llm-review-diffs.sh [base-branch] [output-dir]
```

Standardwerte:

- `base-branch`: `master`
- `output-dir`: `.llm`
- Ausgabedatei: `.llm/review-instruction.md`

Wechsle danach IntelliJ Copilot in den Agent-Modus und lasse Copilot zuerst die erzeugte Datei lesen.

## Anwendungsfälle

- PR Reviewer: erzeugt einen Kontext-Prompt für Pull-Request-Reviews.
- Context for JIRA: erzeugt einen Kontext-Prompt aus einem JIRA-Ticket.

## Designprinzipien

- Externe Skripte sammeln Fakten und erzeugen Prompts.
- Copilot liest den Prompt und setzt die Analyse im Repository fort.
- Prompts sollen aus einer Datei bestehen, nachvollziehbar sein und klare Einschränkungen enthalten.
- Große Diffs sollen standardmäßig vermieden werden, um Kontext zu sparen.
- Prompts sollen Versionsinformationen enthalten.
