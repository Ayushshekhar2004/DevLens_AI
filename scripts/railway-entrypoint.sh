#!/bin/sh
set -eu
# Railway mounts volumes as root. Prepare the mount, then drop privileges.
repository_path="${REPOSITORY_STORAGE_ROOT:-/app/data/repository-snapshots}"
mkdir -p "$repository_path"
chown devlens:devlens "$repository_path"
exec su -s /bin/sh devlens -c 'exec /opt/java/openjdk/bin/java -jar /app/app.jar'
