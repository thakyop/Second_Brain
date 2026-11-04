# CPU scheduling

An operating system decides which ready process runs next on the CPU. This
decision is called CPU scheduling, and the part of the kernel that makes it is
the scheduler.

## First-come, first-served

Processes run in the order they arrive. It is simple, but one long process can
make every short process behind it wait (the convoy effect).

## Round-robin

Each process gets a fixed time slice, called a quantum. When the quantum runs
out, the process goes to the back of the ready queue. A short quantum makes the
system feel responsive but spends more time on context switches; a long one
behaves like first-come, first-served.

## Shortest job first

The scheduler picks the process with the shortest next CPU burst. This gives
the lowest average waiting time, but the length of the next burst has to be
estimated, usually from earlier bursts.
