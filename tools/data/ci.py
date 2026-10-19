import os


def warn(msg):
    if os.environ.get("GITHUB_ACTIONS") == "true":
        msg = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print(f"::warning::{msg}")
    else:
        print(f"WARNING: {msg}")
