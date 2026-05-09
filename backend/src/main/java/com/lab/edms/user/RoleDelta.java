package com.lab.edms.user;

import java.util.Set;

record RoleDelta(Set<String> added, Set<String> removed) {}
