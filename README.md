# DisklessGit

Stateless Git server backed by [Kronotop](https://github.com/kronotop/kronotop).

## Goal

DisklessGit aims to give AI agents a state store that they can version and branch. It is built on Kronotop.
Every repository gets its own namespace, and Kronotop scales with the FoundationDB cluster behind it.

Kronotop also has transactions, document and ordered key/value models, and vector indexes. Agents can use these
directly.

## Install 

DisklessGit requires Java 25+.

Clone the repository:

```
git clone https://github.com/kronotop/disklessgit.git
```

Build the JAR:

```shell
./mvnw clean package -DskipTests
```

DisklessGit uses Kronotop to store all data. The fastest way to try Kronotop is with Docker Compose.

Download the Docker Compose file:

```bash
curl -O https://kronotop.com/kronotop-quickstart.yaml
```

Start the cluster:

```bash
docker compose -f kronotop-quickstart.yaml up
```

This starts a minimal cluster: one FoundationDB instance, one Kronotop primary node, and one standby node.
The primary owns shard 0; the standby replicates it for failover.

Kronotop listens on two ports: 5484 for client traffic and 3320 for cluster administration.

Run the DisklessGit server process:

```shell
disklessgit-server
```

If everything goes well, you should see the startup logs:

```
2026-11-24 21:04:37,099 [ INFO] [main] [c.k.d.DisklessGitInstanceStarter]: DisklessGit, a stateless Git server backed by Kronotop
2026-11-24 21:04:37,100 [ INFO] [main] [c.k.d.DisklessGitInstanceStarter]: Starting DisklessGit 0.1.0 (cd1cf6f, built 2026-11-24T18:19:59+03:00) pid 34302
2026-11-24 21:04:37,101 [ INFO] [main] [c.k.d.DisklessGitInstanceStarter]: Runtime:    Java 27.0.1.1 (Amazon.com Inc.), Mac OS X/aarch64, 12 cores, 12.0 GB heap
2026-11-24 21:04:37,101 [ INFO] [main] [c.k.d.DisklessGitInstanceStarter]: Server:     127.0.0.1:8080
2026-11-24 21:04:37,101 [ INFO] [main] [c.k.d.DisklessGitInstanceStarter]: Management: 127.0.0.1:8081
2026-11-24 21:04:37,101 [ INFO] [main] [c.k.d.DisklessGitInstanceStarter]: Kronotop:   localhost:5484
2026-11-24 21:04:37,101 [ INFO] [main] [c.k.d.DisklessGitInstanceStarter]: Ready to accept connections in 406 ms
```

DisklessGit listens on two ports: 8080 for the Git protocol and 8081 for management.

Push a giant repository to DisklessGit:

```
➜  jdk git:(master) git remote -v
origin  git@github.com:openjdk/jdk.git (fetch)
origin  git@github.com:openjdk/jdk.git (push)
➜  jdk git:(master) git remote add disklessgit http://localhost:8080/openjdk/jdk.git
➜  jdk git:(master) git remote -v
disklessgit     http://localhost:8080/openjdk/jdk.git (fetch)
disklessgit     http://localhost:8080/openjdk/jdk.git (push)
origin  git@github.com:openjdk/jdk.git (fetch)
origin  git@github.com:openjdk/jdk.git (push)
➜  jdk git:(master) git push disklessgit master
Enumerating objects: 1658519, done.
Counting objects: 100% (1658519/1658519), done.
Delta compression using up to 12 threads
Compressing objects: 100% (310452/310452), done.
Writing objects: 100% (1658519/1658519), 1.39 GiB | 47.71 MiB/s, done.
Total 1658519 (delta 1240331), reused 1658108 (delta 1239980), pack-reused 0 (from 0)
remote: Resolving deltas: 100% (1240331/1240331)
remote: Updating references: 100% (1/1)
To http://localhost:8080/openjdk/jdk.git
 * [new branch]            master -> master
➜  jdk git:(master)
```

All data is stored in Kronotop as pack files, together with its metadata.

Check the stored data in Kronotop:

```
127.0.0.1:5484> namespace use disklessgit.openjdk.jdk
OK
127.0.0.1:5484> bucket.query refs {} batch 1
1# "cursor_id" => (integer) 5
2# "entries" => 1) {"_id": "6b03fdc061ebb2e19d0043c3", "name": "refs/heads/master", "target": "effef93993b16cc4f0940b3ed92a8444baf79785", "symbolic": false}
```

Everything is stored in three buckets under the `disklessgit.<owner>.<repo>` namespace.

```
127.0.0.1:5484> bucket.list
1) "chunks"
2) "packs"
3) "refs"
```

## Project status

DisklessGit is under heavy development.

## License

The Apache License, Version 2.0 - see LICENSE for more details.
