# AI-Assisted Project Kickoff

A reusable way to start a software project with an AI coding assistant (e.g. Claude Code): from the
first idea to a repository the assistant can keep working on, step by step, across many conversations.

This guide was distilled from the kickoff of this project. The [README](../README.md),
[CLAUDE.md](../CLAUDE.md) and the roadmap issues are a worked example of the result.

**How to use it:**
1. Copy the [kickoff prompt](#1-kickoff-prompt) below (GitHub shows a copy button on the code block).
2. Fill in the **About my project** block at the top. Leave the rest as it is.
3. Paste it as the first message in a new AI conversation on the (empty or freshly generated) repository.
4. Answer the questions the AI asks, phase by phase.

Part 2 is a short reference for you: the idea behind the process and the pitfalls.

---

## 1. Kickoff prompt

````text
# About my project (fill this in)

- Idea: I want to build <what> for <whom>, so that <which problem it solves>.
- Roles and core features:
  - <role 1, e.g. user>: <what they can do>
  - <role 2, e.g. admin>: <what they can do>
- Tech stack: <languages, frameworks, database, hosting if known>
- What must be stored: <data and its state>
- Repository state: <empty / generated from template X>
- Areas that are new to me (explain these step by step): <e.g. Docker, cloud hosting>
- Purpose: <e.g. real product, learning project, showcase of AI-assisted development>
- Anything else I already know: <constraints, non-goals, deadlines>

# How I want you to work

We will set up this project together in phases. At the end of each phase, STOP and wait for my
answer before you continue. Never answer your own questions on my behalf.

Throughout all phases:
- Ask when something is unclear instead of assuming. If a word looks like a typo (e.g. a framework
  name), ask or state your interpretation.
- Suggest improvements (architecture, security, UX, tooling, workflow) with a short reason. This is
  also a learning project for me.
- Explain the areas I listed as new to me instead of only producing files.
- The repository is the memory, not the chat: every decision and next step ends up in a committed
  file or a GitHub issue. Future conversations start fresh and only see the repository and GitHub.
- Keep commits small and messages descriptive.

## Phase 1: First draft and open questions

1. Look at the repository so you know what already exists.
2. Write a first README.md and CLAUDE.md from my description, using the structures below.
3. Go through the question checklist below. Decide what my description already answers. For
   everything relevant that is still open, ask me, numbered, each with your recommendation and a
   one-line reason. Skip questions that don't apply. Put the open questions in an "Open questions"
   section of the README as well.
4. Commit and push, then STOP and wait for my answers.

## Phase 2: Record the decisions

1. Replace the open questions in the README with a "Decisions" table (topic | decision).
2. Turn decisions that constrain the code into numbered, testable "Domain rules (must hold)" in
   CLAUDE.md (what must never happen, secrets handling, concurrency, access rules).
3. Add the "Workflow" and "Working with the owner" sections to CLAUDE.md (see below).
4. Report what you interpreted or decided yourself, and any new suggestions. Commit and push, then
   STOP.

## Phase 3: Roadmap and issues

1. Propose a roadmap of small steps, each fitting one pull request. The first technical step sets up
   the development environment so that you can build and run tests in your own sessions (for Claude
   Code in the cloud: a SessionStart hook). Critical logic gets tests written first.
2. STOP and wait for my OK on the roadmap.
3. Create one GitHub issue per step with: goal, scope, acceptance criteria as checkboxes ("done
   when…"), dependencies on other issues, and the open questions that belong to that step.
4. Link each roadmap entry in the README to its issue. Commit and push.
5. Remind me to merge this kickoff pull request before starting the next conversation, because new
   conversations start from the default branch. Tell me how to start the next one
   ("Work on issue #N.").

# Question checklist (for you to work through in phase 1)

- Users and access: roles and their permissions; how users get access (public, invite link or
  token, accounts, social login); how admins log in; what must stay hidden.
- Abuse and limits: how it could be misused and how bad that would be; rate limits, quotas,
  per-user tracking, or explicitly none.
- Data: entities, relationships and lifecycle states; which database; how data gets in (form, text
  field, file upload, API) and its edge cases (whitespace, duplicates, empty lines); backups;
  personal data and privacy (e.g. GDPR).
- Business rules: what must never happen; concurrency (two requests at the same moment); time
  (expiry, time zones, store UTC); whether the system or the user chooses.
- UX: the main flow step by step; what the user sees first; empty and error states; mobile vs.
  desktop; language(s); accessibility.
- Tech and architecture: confirm the stack; what to keep from a generated template; where shared
  models and API contracts live; where testable business logic lives.
- Security and secrets: which values are secrets; secrets only via environment variables or a
  secret manager, never in the repository or logs; HTTPS; authentication mechanism; what an
  unauthenticated request may see.
- Deployment and operations: packaging (e.g. one Docker image for frontend and backend); hosting
  target, domain, HTTPS; persistent storage that survives redeploys; configuration per environment;
  CI on every pull request; automatic or manual deployment; what to log and what never to log.
- Process: non-goals (out of scope); whether the project is a showcase (then: small PRs, tests,
  readable history).

# README.md structure

- Title and a one-paragraph pitch (what, for whom, why), plus a status note.
- Features: one subsection per role; the core flow or lifecycle (a small text state diagram helps).
- Decisions: table of topic | decision (after phase 2; before that, "Open questions").
- Architecture: table of part | technology | module; draft domain model.
- Project structure, running the app, running tests, deployment.
- Roadmap: numbered steps, done ones marked ✅, each linked to its issue (after phase 3).

# CLAUDE.md structure

- Project purpose: short summary of the product and roles; point to the README for the full spec
  and say to keep the README in sync.
- Tech stack: languages, frameworks, database, deployment target, UI language, package/namespace.
- Modules: table of module | purpose.
- Commands: build, run, test, lint. Keep current.
- Domain rules (must hold): numbered, testable invariants.
- Conventions: where shared code and business logic live, testing expectations, date/time
  handling, dependency management, code style, commit size.
- Workflow:
  - One roadmap step = one issue = one conversation = one pull request; a conversation starts with
    "Work on issue #N"; read the issue, README and CLAUDE.md first and stay in scope.
  - Reference the issue in the pull request ("Closes #N").
  - Build, test and review your own diff before opening or updating a pull request.
  - At the end of a step: tick it off in the roadmap, update README and CLAUDE.md, record
    follow-ups in issues.
  - Never leave decisions or next steps only in the chat.
- Working with the owner: ask when ambiguous and record decisions in the README; suggest
  improvements; explain the areas that are new to the owner.
````

---

## 2. Reference

### The idea

**The repository is the memory, not the chat.** Every new AI conversation starts fresh and only
knows what is committed to the repository and what is in GitHub. So the spec, decisions,
conventions and next steps all live there:

| File / place | Contains | Audience |
|--------------|----------|----------|
| `README.md` | What the project is: features, decisions, architecture, how to run, roadmap | Humans (and the AI) |
| `CLAUDE.md` | How to work on it: stack, commands, must-hold rules, conventions, workflow | The AI (read automatically at session start) |
| GitHub issues | One per roadmap step: goal, scope, acceptance criteria, open questions | Both |
| Pull requests | The change for one step, linked to its issue | Both |

**One roadmap step = one issue = one conversation = one pull request.** After the kickoff, every
new conversation starts with "Work on issue #N." and needs nothing from earlier chats.

### Your part

- Fill in the **About my project** block honestly, especially the areas that are new to you.
- Answer the questions in phase 1. Every question you skip becomes an assumption the AI makes for you.
- Review every pull request yourself, and merge the kickoff pull request before the next conversation.
- Ask for a code review (e.g. `/code-review`) before merging larger steps.

### Pitfalls

- **Decisions only in the chat:** the next conversation won't know them. Make sure they are committed.
- **Starting a new conversation before merging:** it starts from the default branch and misses the latest docs.
- **The AI can't build or test in its environment:** code gets written but not verified. Set this up first.
- **Huge pull requests:** hard to review and hard to undo. One roadmap step per pull request.
- **Vague acceptance criteria:** "done" becomes a matter of opinion. Use checkboxes.
- **Trusting generated code for critical rules:** pin those rules down with tests written first.

### Possible next step: a skill

The kickoff prompt can be turned into a Claude Code skill (e.g. `/project-kickoff`), so you only
describe the idea instead of pasting the prompt. Worth it once the prompt has been used and refined
on a second project.
