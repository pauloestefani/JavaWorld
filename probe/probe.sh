#!/usr/bin/env bash
set -u
code() { curl -s -o /dev/null -w "%{http_code}" -L "$1"; }
echo "### Fabric server jar 26.3: $(code https://meta.fabricmc.net/v2/versions/loader/26.3/0.20.1/1.1.2/server/jar)"
echo "### Fabric server jar 1.21.1: $(code https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.20.1/1.1.2/server/jar)"
curl -s "https://meta.fabricmc.net/v2/versions/loader/1.12.2" | head -c 200; echo
echo "### Forge versions json"
curl -s https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json | jq -r '.promos | keys | length'
curl -s https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml | grep -o '<version>[^<]*</version>' | tail -3
echo "### NeoForge list sample"
curl -s "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge" | jq -r '.versions | map(select(startswith("21.1."))) | .[-2:][]'
curl -s "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge" | jq -r '.versions | map(split(".")[0:2] | join(".")) | unique | .[]' | tr '\n' ' '; echo
echo "### Modrinth versions sample"
curl -s -A "pauloestefani/JavaWorld/probe" 'https://api.modrinth.com/v2/project/lithium/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%2226.3%22%5D' | jq -r '.[0] | "\(.version_number) \(.files[0].filename) deps=\(.dependencies|length)"'
curl -s -A "pauloestefani/JavaWorld/probe" 'https://api.modrinth.com/v2/search?limit=3&index=downloads&facets=%5B%5B%22project_type%3Amod%22%5D%2C%5B%22categories%3Afabric%22%5D%2C%5B%22versions%3A26.3%22%5D%2C%5B%22server_side%3Arequired%22%2C%22server_side%3Aoptional%22%5D%5D' | jq -r '.hits[] | "\(.slug) \(.server_side) \(.icon_url)"'

for spec in "forge 26.3-66.1.5 https://maven.minecraftforge.net/net/minecraftforge/forge/26.3-66.1.5/forge-26.3-66.1.5-installer.jar" \
            "forge 1.20.1-47.4.0 https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.0/forge-1.20.1-47.4.0-installer.jar" \
            "forge 1.12.2-14.23.5.2860 https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2860/forge-1.12.2-14.23.5.2860-installer.jar" \
            "neoforge 26.3.0.102 https://maven.neoforged.net/releases/net/neoforged/neoforge/26.3.0.102/neoforge-26.3.0.102-installer.jar"; do
  set -- $spec
  d=inst-$1-$2; mkdir -p $d; echo "### INSTALL $1 $2 ($(code $3))"
  ( cd $d && curl -sL -o installer.jar "$3" && timeout 600 java -jar installer.jar --installServer > install.log 2>&1; echo "exit=$?"; tail -3 install.log
    ls -la; echo "--- run.sh:"; cat run.sh 2>/dev/null | grep -v '^#' | grep -v '^$'
    echo "--- args files:"; find . -name '*args*.txt' | head; f=$(find . -name 'unix_args.txt' | head -1); [ -n "$f" ] && head -c 600 "$f"; echo
    find . -maxdepth 1 -name '*.jar' )
done
