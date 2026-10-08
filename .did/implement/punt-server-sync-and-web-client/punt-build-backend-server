Build initial Punt backend synchronization server

Implement the core Punt backend server sync APIs over local network HTTP for storing and merging reminder patches and punt timestamps across devices.
The backend focuses strictly on synchronization (no query endpoints) and supports a patch-like delta merging protocol to avoid overwriting concurrent client contributions.
Authentication uses temporary single-use setup codes for initial device pairing instead of username/password authentication, issuing persistent session tokens for subsequent synchronization.
