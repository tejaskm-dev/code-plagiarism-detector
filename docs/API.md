# REST API

The server started by `integrity serve` (or `bin/integrity-server`) serves the web UI at
`/` and this API under `/api/v1`. The web UI is built entirely on these routes, so anything
it does, a script can do too.

The server is an application built on the library and is used for the project demo. To add
detection to your own Java code, use the library directly, as described in the
[README](../README.md).

```sh
java -jar integrity-engine.jar serve --port 7070 --db integrity-server.db
```

> **There is no authentication.** Anyone who can reach the port can read every stored
> submission. Run it on a trusted machine, or put it behind a reverse proxy that
> authenticates users before exposing it on a network.

## Conventions

- **Responses** are JSON. Successful calls return `200`, or `201` when something is created.
- **Errors** always have this shape, whatever the status code:

  ```json
  {"error": "unknown_assignment", "message": "no assignment with id 3f9c2a1b"}
  ```

  `error` is a stable code you can branch on. `message` is for people to read and may change.
  An unmatched route under `/api/` returns `404` with `"error": "unknown_route"`.
- **Requests** send simple fields as form data (`application/x-www-form-urlencoded`)
  and files as `multipart/form-data`. Every file part is read, whatever its field name.
  The UI uses `files`.
- **Analysis goes stale.** Any change to an assignment's submissions or reference files
  throws away its analysis. `results` and `pair` then return `409 not_analysed` until you
  run `analyze` again. Responses that cause this include `"analysisInvalidated": true`.
- **Restarts** keep assignments, submissions and reference files, which all live in the
  SQLite file. Analyses are not kept, so run `analyze` again after a restart.

## Quick walkthrough

```sh
API=http://localhost:7070/api/v1

ID=$(curl -s -X POST -d name=CS101-HW3 $API/assignments | jq -r .id)
curl -s -F files=@examples/cs101-hw3.zip $API/assignments/$ID/submissions
curl -s -X POST $API/assignments/$ID/analyze
curl -s $API/assignments/$ID/results | jq '.pairs | sort_by(-.score) | .[:3]'
```

## Routes

| Method | Path | Purpose |
| :--- | :--- | :--- |
| `GET` | `/api/v1/health` | Check that the server is up |
| `POST` | `/api/v1/assignments` | Create an assignment |
| `GET` | `/api/v1/assignments` | List assignments, newest first |
| `GET` | `/api/v1/assignments/{id}` | Get one assignment |
| `DELETE` | `/api/v1/assignments/{id}` | Delete an assignment and everything stored under it |
| `POST` | `/api/v1/assignments/{id}/submissions` | Upload source files or a ZIP |
| `GET` | `/api/v1/assignments/{id}/submissions` | List the stored submissions |
| `PATCH` | `/api/v1/assignments/{id}/submissions/{sid}` | Assign a submission to a different student |
| `DELETE` | `/api/v1/assignments/{id}/submissions/{sid}` | Remove one submission |
| `POST` | `/api/v1/assignments/{id}/boilerplate` | Upload starter-code reference files |
| `DELETE` | `/api/v1/assignments/{id}/boilerplate` | Remove all reference files |
| `POST` | `/api/v1/assignments/{id}/analyze` | Run the analysis |
| `GET` | `/api/v1/assignments/{id}/results` | Get the full results |
| `GET` | `/api/v1/assignments/{id}/pair?left=&right=` | Get side-by-side detail for one pair |

Every route that takes `{id}` returns `404 unknown_assignment` if the assignment does
not exist.

---

### `GET /api/v1/health`

Returns without touching the database. Use it to check the server is up.

```json
{"status": "ok", "version": "0.1.0"}
```

### `POST /api/v1/assignments`

| Field | Required | Notes |
| :--- | :--- | :--- |
| `name` | no | At most 120 characters. Defaults to `assignment`. |

`201`:

```json
{"id": "3f9c2a1b", "name": "CS101-HW3", "createdAt": "2026-09-16T13:18:43.431775Z"}
```

Errors: `400 name_too_long`.

### `GET /api/v1/assignments`

```json
[{"id": "3f9c2a1b", "name": "CS101-HW3", "analysed": false}]
```

### `GET /api/v1/assignments/{id}`

```json
{
  "id": "3f9c2a1b", "name": "CS101-HW3", "createdAt": "2026-09-16T13:18:43.431775Z",
  "submissionCount": 10, "referenceFileCount": 0, "analysed": true
}
```

### `DELETE /api/v1/assignments/{id}`

Deletes the assignment's results, submissions and reference files, then the assignment
itself. This cannot be undone.

```json
{"deleted": "3f9c2a1b", "submissionsDeleted": 10}
```

### `POST /api/v1/assignments/{id}/submissions`

A multipart upload of source files, `.zip` archives, or both.

**How each file is matched to a student**, trying each rule in order:

1. **Folder:** in a ZIP, the first folder in the file's path, e.g. `student01_alice/Gradebook.java`.
2. **Roll number in the filename:** a token such as `22CS101`, or a student number of
   five or more digits.
3. **Filename within the batch:** in a batch of three or more files, the parts of each
   filename that no other file shares.
4. **Unidentified:** otherwise the file gets a placeholder student, `unidentified-N`.
   It is still compared with the rest, and you can fix the student later with `PATCH`.

**Limits:** 2 MB for each source file. A ZIP can be up to 64 MB and hold up to 2000
entries. Files with any other extension are rejected. The supported extensions are:

| Language | Extensions |
| :--- | :--- |
| Java | `.java` |
| Python | `.py`, `.pyw` |
| C | `.c` |
| C++ | `.cpp`, `.cc`, `.cxx`, `.c++`, `.hpp`, `.hh`, `.hxx` |
| JavaScript | `.js`, `.mjs`, `.cjs`, `.jsx` |

C headers (`.h`) are not supported.

`200` if at least one file was accepted. `400` if none were:

```json
{
  "acceptedCount": 10, "storedTotal": 10, "unidentifiedCount": 0,
  "accepted": [{"filename": "Gradebook.java", "path": "student08_hana/Gradebook.java",
                "student": "student08_hana", "identifiedBy": "folder"}],
  "rejected": [{"filename": "cs101-hw3/student05_evan/notes.txt",
                "reason": "unrecognised source extension"}]
}
```

`identifiedBy` is `folder`, `filename` or `unidentified`. `storedTotal` counts what is
actually stored after this upload. It can be lower than the number of files sent, because
an upload that reuses a path replaces the stored file.

Errors: `400 no_files`, `400 malformed_upload`.

### `GET /api/v1/assignments/{id}/submissions`

```json
{
  "assignmentId": "3f9c2a1b", "assignmentName": "CS101-HW3",
  "createdAt": "2026-09-16T13:18:43.431775Z",
  "count": 10, "unidentifiedCount": 0, "analysed": false, "referenceFileCount": 0,
  "submissions": [{
    "submissionId": "3f9c2a1b/student01_alice/Gradebook.java", "student": "student01_alice",
    "filename": "Gradebook.java", "language": "java",
    "lineCount": 71, "byteCount": 1729, "unidentified": false
  }]
}
```

A `submissionId` can contain `/`. That is fine in the `{sid}` routes below. In a query
string, URL-encode it.

### `PATCH /api/v1/assignments/{id}/submissions/{sid}`

| Field | Required | Notes |
| :--- | :--- | :--- |
| `student` | yes | At most 80 characters. Cannot start with `unidentified-`. |

```json
{"submissionId": "3f9c2a1b/unidentified-1/hw.java", "student": "22CS101", "analysisInvalidated": true}
```

Errors: `404 unknown_submission`, `400 missing_student`, `400 student_too_long`,
`400 reserved_identifier`.

### `DELETE /api/v1/assignments/{id}/submissions/{sid}`

```json
{"deleted": "3f9c2a1b/student05_evan/Gradebook.java", "remaining": 9, "analysisInvalidated": true}
```

Errors: `404 unknown_submission`.

### `POST /api/v1/assignments/{id}/boilerplate`

Upload the starter or skeleton files you gave every student. At the next `analyze`, any
code that matches these files is removed from every submission before files are compared.
Matching on code you provided then does not count against anyone.

Separately, the engine removes code that appears in almost every submission (80% or more
of the class), but it only does that for classes of five or more. Reference files are
applied at any class size, because they are known to be starter code.

Files are stored by filename, so uploading the same name again replaces the old copy. A
file with an unrecognised extension, or larger than 2 MB, is rejected.

`200` if at least one file was stored:

```json
{
  "stored": ["Gradebook.java"], "rejected": [], "referenceFileCount": 1,
  "applied": true, "analysisInvalidated": true,
  "note": "Starter code in these files is excluded from every comparison. Re-run the analysis to see updated scores."
}
```

Errors: `400 no_files`, `400 malformed_upload`, `400 no_usable_reference_files` (none of
the files could be used; `message` says why for each one).

### `DELETE /api/v1/assignments/{id}/boilerplate`

```json
{"removed": 1, "analysisInvalidated": true}
```

### `POST /api/v1/assignments/{id}/analyze`

Compares every pair of submissions and flags the pairs that stand out for this class.

The analysis always includes the offline AI-authorship model. To also get an LLM second
opinion, send your own Anthropic key in the `X-LLM-Api-Key` header. The key is used for
this request only. It is never stored, logged or included in a response.

```json
{"assignmentId": "3f9c2a1b", "submissionCount": 10, "pairCount": 45, "flaggedCount": 6, "aiResultCount": 10}
```

Errors: `400 no_submissions`.

### `GET /api/v1/assignments/{id}/results`

The full analysis. Top-level fields:

| Field | Contents |
| :--- | :--- |
| `cohort` | Class-wide statistics: `median`, `mad`, `mae`, `reviewThreshold` (the score a pair must reach to be flagged), `outlierThreshold`, `tier` / `tierLabel` (which flagging rule applied), and counts |
| `pairs` | One entry per pair: `left`, `right` (submission ids), `leftLabel`, `rightLabel`, `score`, `metric`, `containmentLeftInRight`, `containmentRightInLeft`, `modifiedZ`, `flagged`. Flagged pairs also include `sharedFingerprints`, `leftSpans` and `rightSpans` (line ranges) |
| `files` | One entry per submission: `submissionId`, `student`, `filename`, `source`, plus `aiScore`, `aiVerdict` (`generated`, `human` or `unsure`), `aiRationale` and `aiModel` |
| `ai` | Counts across the class: `measured`, `generated`, `human`, `unsure`, and the `band` of uncertainty around 0.5 |
| `referenceFilesStored` | How many reference files this analysis used |

Errors: `409 not_analysed`.

### `GET /api/v1/assignments/{id}/pair?left={sid}&right={sid}`

Detail for reviewing one pair. It works for any pair, not only flagged ones, so you can
check a pair that just missed being flagged.

| Field | Contents |
| :--- | :--- |
| `left`, `right` | `submissionId`, `student`, `filename`, `source`, `lineCount`, `matchedLines` and `spans` (the line ranges that match the other file) |
| `score`, `modifiedZ`, `flagged` | As in `results` |
| `containmentLeftInRight`, `containmentRightInLeft` | How much of one file appears in the other. Useful when one file is much larger |
| `cohortMedian`, `cohortMad`, `reviewThreshold` | The class-wide numbers the pair was judged against |
| `sharedFingerprints`, `matchedLineCount` | How much the two files share |
| `evidence` | `routines` (methods that match each other), `renames` (identifiers that were renamed consistently), `renamedTokens`, `identicalTokens` |

Errors: `409 not_analysed`, `400 missing_pair`, `404 unknown_submission`.
