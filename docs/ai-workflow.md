# AI-Assisted Project Kickoff Guide

A reusable recipe for starting a software project with an AI coding assistant (e.g. Claude Code):
from the first idea to a repository the assistant can work on, step by step, across many conversations.

This guide was distilled from the kickoff of this project: the [README](../README.md),
[CLAUDE.md](../CLAUDE.md) and the roadmap issues are a worked example of it.

---

## 1. The core idea

**The repository is the memory, not the chat.**

Every new AI conversation starts fresh. It only knows what is committed to the repo and what is in
GitHub (issues, PRs). So everything that matters, like the spec, decisions, conventions and the next
steps, has to be written down there.

| File / place | Contains | Audience |
|--------------|----------|----------|
| `README.md` | What the project is: features, decisions, architecture, how to run, roadmap | Humans (and the AI) |
| `CLAUDE.md` | How to work on it: stack, commands, must-hold rules, conventions, workflow | The AI (read automatically at session start) |
| GitHub issues | One per roadmap step: goal, scope, acceptance criteria, open questions | Both |
| Pull requests | The actual change for one step, linked to its issue | Both |

**Workflow:** one roadmap step = one issue = one conversation = one pull request.

---

## 2. The kickoff sequence

### Step 1: Describe the idea (first conversation)
Give the assistant the idea, the users, the core features and the tech stack. Ask it to:
- write a first `README.md` and `CLAUDE.md`,
- list **open questions** instead of guessing,
- suggest improvements, because you want to learn.

Kickoff prompt template:

> I want to build **[what]** for **[who]**, so that **[problem it solves]**.
> Core features: **[list]**. Tech stack: **[list]**. Persistence: **[what must be stored]**.
> I want to develop this step by step; it is also a learning project, so point out improvements.
> As a first step, create a README.md and CLAUDE.md from this. If anything is unclear, list the open
> questions with your recommendation for each, and let me decide.

### Step 2: Answer the open questions
Answer them (the checklist in section 4 helps), and ask the assistant to record the answers in a
**Decisions** table in the README and as rules in CLAUDE.md.

### Step 3: Turn the roadmap into issues
Ask the assistant to create one GitHub issue per roadmap step with:
- goal,
- scope,
- acceptance criteria ("done when…" checkboxes),
- dependencies on other issues,
- open questions that belong to that step.

Link each roadmap entry in the README to its issue.

### Step 4: Merge the kickoff PR
**Important:** new conversations start from the default branch (`main`). Until the kickoff branch is
merged, a new session won't see the README, CLAUDE.md or the workflow rules.

### Step 5: One conversation per issue
Start each new conversation with:

> Work on issue #N.

The first technical issue should be the **dev environment setup** (e.g. a SessionStart hook for cloud
sessions), so the assistant can build and run tests in every later step.

---

## 3. Templates

### README.md structure

```markdown
# <Project name>
One-paragraph pitch: what it does, for whom, why.
> Status: early stage / what's built so far.

## Features
### For <user role 1>
### For <user role 2>
### <Core lifecycle or flow>   (a small state diagram helps a lot)

### Decisions
| Topic | Decision |
|-------|----------|

## Architecture
| Part | Technology | Module |
### Domain model (draft)

## Project structure
## Running the app
## Running tests
## Deployment

## Roadmap
Each step is one issue, one conversation and one PR.
1. ✅ Specification
2. [#N](link) <step>
...
```

### CLAUDE.md structure

```markdown
# CLAUDE.md
Guidance for AI assistants working in this repository.

## Project purpose
Short summary of the product and its roles. Point to the README for the full spec;
keep the README in sync when requirements or decisions change.

## Tech stack
Languages, frameworks, database, deployment target, UI language, package/namespace.

## Modules
| Module | Purpose |

## Commands
Build, run, test, lint. Keep this list current.

## Domain rules (must hold)
Numbered, testable invariants: what must never happen
(e.g. "a code is handed out at most once", "unknown tokens reveal nothing",
"secrets come from environment variables, never logged").

## Conventions
Where shared code lives, where business logic lives (testable, outside UI and routes),
testing expectations, date/time handling, dependency management, code style, commit size.

## Workflow
- One step = one issue = one conversation = one pull request. Start with "Work on issue #N".
- Reference the issue in the PR (`Closes #N`).
- Build, test and self-review before opening or updating a PR.
- At the end of a step: tick off the roadmap, update README/CLAUDE.md, record follow-ups in issues.
- Never leave decisions or next steps only in the chat.

## Working with the owner
- Ask when the spec is ambiguous; record decisions in the README.
- Suggest improvements; this is a learning project.
- Areas where I'm inexperienced (explain each step there): <e.g. Docker, cloud hosting>.
```

---

## 4. Checklist: questions to answer before building

Not every item applies to every project, but go through the list once. Each one you skip becomes an
assumption the AI makes for you.

### Users and access
- [ ] Who are the user roles (e.g. user, admin)? What can each one do?
- [ ] How do users get access: public, invite link or token, accounts, social login?
- [ ] How do admins log in? One admin from configuration, or several accounts?
- [ ] Is there anything that must stay hidden (e.g. no public list of items)?

### Abuse and limits
- [ ] Can someone misuse it (scraping, draining resources, spam)? How bad would it be?
- [ ] Do you need rate limits, quotas or per-user tracking? Or is that explicitly not needed?

### Data
- [ ] What entities exist, and what are their relationships and lifecycle (states)?
- [ ] Which database? (Simple: SQLite. Hosted or multi-instance: PostgreSQL.)
- [ ] How does data get in: form, text field, CSV upload, API? Which formats and edge cases
      (whitespace, duplicates, empty lines)?
- [ ] Backups: how and how often?
- [ ] Personal data and privacy (e.g. GDPR): what is stored, and is it needed at all?

### Business rules (invariants)
- [ ] What must **never** happen? (Double hand-out, duplicates, data leaks, expired items being used.)
- [ ] Concurrency: what if two requests hit the same thing at the same moment?
- [ ] Time: expiry dates, time zones (store UTC).
- [ ] Ordering and choice: does the system decide, or does the user pick?

### UX
- [ ] Main user flow, step by step. What does the user see first?
- [ ] Empty and error states: nothing available, invalid link, network error.
- [ ] Mobile vs. desktop: where will most users open it?
- [ ] Language(s): one language, or i18n from the start?
- [ ] Accessibility requirements?

### Tech and architecture
- [ ] Confirm the stack and spell out framework names (a typo can lead to a wrong guess).
- [ ] Is the starting point a generated template? What should be kept or replaced?
- [ ] Where do shared models and API contracts live, so client and server can't drift apart?
- [ ] Where does business logic live, so it can be tested without UI or HTTP?

### Security and secrets
- [ ] Which values are secrets (passwords, tokens, keys, the data itself)?
- [ ] Secrets only via environment variables or a secret manager, never in the repo or logs.
- [ ] HTTPS, authentication mechanism, what an unauthenticated request may see.

### Deployment and operations
- [ ] Packaging: Docker image? Does one deployable serve both frontend and backend?
- [ ] Hosting target (e.g. Fly.io, a VPS, cloud), domain, HTTPS.
- [ ] Persistent storage in production (volumes) and how it survives a redeploy.
- [ ] Configuration per environment (local, production).
- [ ] CI: build and tests on every PR; automatic deployment or manual?
- [ ] Logging and monitoring: what to log, what never to log.

### Process and learning goals
- [ ] Which areas are new to you, where the AI should explain rather than just produce files?
- [ ] Is the project also a showcase? Then you want small PRs, tests and a readable history.
- [ ] What is explicitly **out of scope** (non-goals)?

---

## 5. Checklist: things to ask the AI for

- [ ] **Open questions with recommendations** instead of silent assumptions.
- [ ] A **Decisions** table in the README, kept up to date.
- [ ] **Domain rules** in CLAUDE.md as numbered, testable invariants.
- [ ] A **Workflow** section in CLAUDE.md (one issue / conversation / PR, handoff at the end of each step).
- [ ] **GitHub issues** per roadmap step with acceptance criteria, linked from the roadmap.
- [ ] **Dev environment setup first** (e.g. a SessionStart hook), so the AI can build and test in
      cloud sessions and check its own work.
- [ ] **Tests first** for the critical logic (the invariants), including concurrency tests where relevant.
- [ ] **Self-review before each PR** (e.g. `/code-review`), plus your own review of the PR.
- [ ] **CI** that runs build and tests on every PR.
- [ ] **Explanations** in the areas you named as new to you.
- [ ] **Improvement suggestions**: architecture, security, UX, tooling.

---

## 6. Pitfalls to avoid

- **Decisions only in the chat:** the next conversation won't know them. Always commit them.
- **Starting a new conversation before merging:** it starts from `main` and misses the latest docs.
- **No way to build or test in the AI's environment:** code gets written but not verified. Set this up first.
- **Huge PRs:** hard to review and hard to undo. One roadmap step per PR.
- **Vague acceptance criteria:** "done" becomes a matter of opinion. Write checkboxes.
- **Trusting generated code for critical rules:** pin those rules down with tests written first.
