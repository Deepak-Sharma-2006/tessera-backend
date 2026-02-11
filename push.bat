@echo off
cd /d D:\tessera-backend

REM Abort any pending merges
if exist .git\MERGE_HEAD (
    del .git\MERGE_HEAD
    del .git\MERGE_MSG 2>nul
)

REM Configure git to not use editor
git config core.editor true

REM Try pushing
git push origin main

REM If push fails due to non-fast-forward, try rebase
if errorlevel 1 (
    echo Push failed, attempting rebase...
    git fetch origin main
    git rebase origin/main
    git push origin main
)

pause
