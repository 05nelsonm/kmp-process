#!/usr/bin/env bash
# Copyright (c) 2024 Matthew Nelson
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
set -e

readonly DIR_SCRIPT="$( cd "$( dirname "$0" )" >/dev/null && pwd )"

trap 'rm -rf "$DIR_SCRIPT/kmp-process"' EXIT

cd "$DIR_SCRIPT"
git clone -b gh-pages --single-branch https://github.com/05nelsonm/kmp-process.git
rm -rf "$DIR_SCRIPT/kmp-process/"*
echo "kmp-process.matthewnelson.io" > "$DIR_SCRIPT/kmp-process/CNAME"

cd ..
./gradlew clean -DKMP_TARGETS_ALL
./gradlew dokkaHtmlMultiModule --no-build-cache -DKMP_TARGETS_ALL
cp -aR build/dokka/htmlMultiModule/* gh-pages/kmp-process

cd "$DIR_SCRIPT/kmp-process"
sed -i "s|module:process|module:library/process|g" "package-list"

git add --all
git commit -S --message "Update dokka docs"
git push
