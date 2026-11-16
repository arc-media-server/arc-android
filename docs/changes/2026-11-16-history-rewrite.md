# History rewritten once before going public

As the server's checklist for making a repository public asks (arc-media-server
`docs/ALPHA-CHECKLIST.md`), the agent session links were removed from this repository's history
once, on 16 November 2026.

- A scan of every version of every file on every branch (107 commits) found no secrets, personal
  names, emails, paths or media; the only addresses are examples (192.168.1.20, 10.0.0.5).
- `git filter-repo --message-callback` removed the `Claude-Session:` trailer from 71 commit
  messages. Every rewritten commit has the same files, author, committer and dates as before.
- No documentation referred to old commit ids. Issue comments that quote old ids (the to-do list,
  #2) aren't rewritten; GitHub keeps each pull request's original commits, so their Commits tabs
  can still show the old messages. Local copies need re-cloning.
