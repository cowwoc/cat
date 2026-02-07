#!/usr/bin/env python3
"""SessionStart hook: clean up accumulated skill marker files from previous sessions.

Removes /tmp/cat-skills-loaded-* files that track which skills have been loaded in each session.
These files accumulate over time as sessions start and end.
"""

import glob
import os


def main():
    for path in glob.glob("/tmp/cat-skills-loaded-*"):
        try:
            os.remove(path)
        except OSError:
            pass


if __name__ == "__main__":
    main()
