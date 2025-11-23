Effect files for the Bice Box project.

NOTE: on PI, you may need to stop
   sudo systemctl stop amidiminder
for roto control to work.

## Maintaining Shared Utilities Across Branches

We maintain a shared infrastructure (like `utilities/init.sc`) across multiple branches (`main`, `experimental`, `midi`, etc.) while keeping the effect files unique to each branch.

To update the utilities in a feature branch with the latest version from `main`:

1.  **Checkout your target branch** (e.g., `experimental`):
    ```bash
    git checkout experimental
    ```

2.  **Pull specific files/folders from main**:
    This updates only the specified path without merging other changes (like different effect files).
    ```bash
    # To update the entire utilities folder (Recommended)
    git checkout main -- utilities/

    # OR to update just the init script
    git checkout main -- utilities/init.sc
    ```

3.  **Commit the changes**:
    ```bash
    git commit -m "Sync utilities from main"
    ```
