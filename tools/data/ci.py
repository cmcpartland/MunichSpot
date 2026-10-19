import os


def warn(msg):
    if record_dir := os.environ.get("RUN_RECORD_DIR"):
        os.makedirs(record_dir, exist_ok=True)
        with open(os.path.join(record_dir, "warnings.log"), "a", encoding="utf-8") as f:
            f.write(str(msg).replace("\n", " ") + "\n")
    if os.environ.get("GITHUB_ACTIONS") == "true":
        msg = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print(f"::warning::{msg}")
    else:
        print(f"WARNING: {msg}")
