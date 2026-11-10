#!/usr/bin/env bash
set -u
echo "### SEARCH repos"
gh api "search/repositories?q=android+openjdk+build&sort=updated&per_page=15" --jq '.items[] | "\(.full_name) stars=\(.stargazers_count) pushed=\(.pushed_at)"' || true

REPOS="PojavLauncherTeam/android-openjdk-build-multiarch AngelAuraMC/angelauramc-openjdk-build FCL-Team/Android-OpenJDK-Build Vera-Firefly/android-openjdk-autobuild MojoLauncher/android-openjdk-build ShirosakiMio/android-openjdk-build-multiarch"
: > assets.txt
for r in $REPOS; do
  echo "### RELEASES $r"
  gh api "repos/$r/releases?per_page=8" --jq '.[] | "TAG \(.tag_name) \(.published_at)\n" + ([.assets[] | "  \(.name) \(.size) \(.browser_download_url)"] | join("\n"))' 2>&1 | head -80 | tee -a assets.txt
  echo "### ARTIFACTS $r"
  gh api "repos/$r/actions/artifacts?per_page=15" --jq '.artifacts[] | "  \(.name) \(.size_in_bytes) expired=\(.expired) \(.created_at)"' 2>&1 | head -20
done

echo "### Pojav assets dir"
gh api "repos/PojavLauncherTeam/PojavLauncher/contents/app_pojavlauncher/src/main/assets/components" --jq '.[].name' 2>&1 | head
gh api "repos/PojavLauncherTeam/PojavLauncher/contents/app_pojavlauncher/src/main/assets/components/jre" --jq '.[] | "\(.name) \(.size)"' 2>&1 | head
echo "### Amethyst tree search for jre"
gh api "repos/AngelAuraMC/Amethyst-Android/git/trees/HEAD?recursive=1" --jq '.tree[].path' 2>&1 | grep -iE 'jre|openjdk|runtime' | head -40
echo "### Amethyst gradle refs to jre"
for f in app_pojavlauncher/build.gradle build.gradle; do gh api "repos/AngelAuraMC/Amethyst-Android/contents/$f" --jq .content 2>/dev/null | base64 -d 2>/dev/null | grep -inE 'jre|openjdk|download' | head -30; done
echo "### Pojav gradle refs to jre"
for f in app_pojavlauncher/build.gradle; do gh api "repos/PojavLauncherTeam/PojavLauncher/contents/$f" --jq .content 2>/dev/null | base64 -d 2>/dev/null | grep -inE 'jre|openjdk|download' | head -30; done

echo "### FRP latest"
gh api repos/fatedier/frp/releases/latest --jq '.tag_name, (.assets[] | select(.name|test("linux_(arm64|amd64)")) | "  \(.name) \(.browser_download_url)")'

echo "### Mojang"
curl -s https://piston-meta.mojang.com/mc/game/version_manifest_v2.json > manifest.json
jq -r '.latest' manifest.json
jq -r '.versions[] | select(.type=="release") | .id' manifest.json | head -15
for v in $(jq -r '.versions[] | select(.type=="release") | .id' manifest.json | head -4) 1.20.4 1.18.2 1.16.5 1.12.2; do
  url=$(jq -r --arg v "$v" '.versions[] | select(.id==$v) | .url' manifest.json)
  curl -s "$url" | jq -r --arg v "$v" '"\($v) java=\(.javaVersion.majorVersion) server=\(.downloads.server.url)"'
done

echo "### Fabric meta"
curl -s https://meta.fabricmc.net/v2/versions/loader | jq -r '.[0:3][] | "\(.version) stable=\(.stable)"'
curl -s https://meta.fabricmc.net/v2/versions/installer | jq -r '.[0:3][] | "\(.version) stable=\(.stable)"'
curl -s https://meta.fabricmc.net/v2/versions/game | jq -r '[.[] | select(.stable)][0:5][] | .version'
echo "### Forge promotions"
curl -s https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json | jq -r '.promos | to_entries | .[-12:][] | "\(.key)=\(.value)"'
echo "### NeoForge"
curl -s "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge" | jq -r '.versions[-5:][]'
echo "### Modrinth"
curl -s -A "javaworld-probe" 'https://api.modrinth.com/v2/search?query=sodium&facets=%5B%5B%22project_type%3Amod%22%5D%5D&limit=2' | jq -r '.hits[] | "\(.slug) \(.title)"'
echo "### Paper fill v3"
curl -s https://fill.papermc.io/v3/projects/paper | jq -c '.' | head -c 600; echo
curl -s https://api.papermc.io/v2/projects/paper | jq -c '.versions[-5:]' 2>/dev/null

echo "### Pick JREs"
mkdir -p dl
grep -iE '^  .*(jre|jdk).*(x86_64|amd64)' assets.txt | head -20
grep -iE '^  .*(jre|jdk).*(arm64|aarch64)' assets.txt | head -20
