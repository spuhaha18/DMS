package com.lab.edms.user;

import java.util.Set;

public record RoleDelta(Set<String> added, Set<String> removed) {}
