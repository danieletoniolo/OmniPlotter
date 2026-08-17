# Local Maven repository

`maven.config` pins Maven's repository to `.mvn/repo` inside the project, so builds never read or
write the user's shared `~/.m2`. Combined with `libs/` (populated by `maven-dependency-plugin` at
package time) a fresh clone plus `./setup.sh` is entirely self-contained.

Both directories are ignored by git; `./setup.sh` repopulates them.
