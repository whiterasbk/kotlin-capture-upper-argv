# CaptureUpperArg Kotlin Compiler Plugin
CaptureUpperArg is a Kotlin IR compiler plugin designed to eliminate boilerplate code by automatically "capturing" arguments from the calling context and injecting them into function parameters.

Think of it as compile-time Dependency Injection for function arguments.

## Overview
In many architectures (like logging, tracing, or context-passing), you often find yourself passing the same variables through multiple layers of functions.

```kotlin
fun topLevel(traceId: String) {
    serviceLayer(traceId)
}

fun serviceLayer(traceId: String) {
    repositoryLayer(traceId)
}

fun repositoryLayer(traceId: String) {
    log(traceId, "Processing...")
}
```

With CaptureUpperArg, the compiler handles this for you:

``` kotlin
// 1. Mark your parameter
fun log(@CaptureUpperArg(collect = false) traceId: String = placeholder, msg: String) {
    println("[$traceId] $msg")
}

// 2. Just call it. If 'traceId' exists in the caller's scope, it's injected!
fun repositoryLayer(traceId: String) {
    log(msg = "Processing...") // traceId is injected by the compiler
}
```

## Features
- Type-Safe Injection: Matches variables based on strict Kotlin type systems (including nullability checks).
- Deep Generic Matching: Recursively checks generic type parameters (e.g., Map<String, List<Int>>).
- Annotation Filtering: Only capture variables that have specific annotations.
- Collection Collection: Automatically aggregate all matching variables in a scope into a List, Set, or Map.
- Recursive Support: Safely handles recursive function calls.
- Compile-time Validation: Reports errors/warnings if no matching variable is found for non-nullable parameters.

## Installation

Add the plugin to your build.gradle.kts:
```kotlin
plugins {
    id("ink.iowoi.kotlin.capture-upper-arg") version "0.0.1"
}
```

## Usage Guide

1. Simple Capture
   The plugin searches the caller's parameters for a matching type.

```kotlin
fun notify(@CaptureUpperArg(collect = false) userId: Long = placeholder) { ... }

fun handleRequest(userId: Long) {
    notify() // Compiler rewrites to: notify(userId)
}
```

2. Multi-Collect Mode
   If the target parameter is a collection, the plugin gathers all matching arguments.

```kotlin
fun handle(@CaptureUpperArg(collect = true) tags: List<String> = placeholder) { ... }

fun context(role: String, category: String) {
    handle() // Compiler rewrites to: handle(listOf(role, category))
}
```

3. Annotation Filtering
   Refine the search by requiring specific annotations on the source parameters.

```kotlin
annotation class Sensitive

fun process(@CaptureUpperArg(annotations = [Sensitive::class], collect = false) secret: String = placeholder) { ... }

fun flow(@Sensitive token: String, publicName: String) {
    process() // Only 'token' is captured because it is marked @Sensitive
}
```

## Configuration (@CaptureUpperArg)

| Property               | Type            | Description                                                                 |
|------------------------|-----------------|-----------------------------------------------------------------------------|
| `annotations`          | `Array<KClass>` | Only capture variables marked with these annotations.                       |
| `exclude`              | `Array<String>` | Ignore variables with these specific names.                                 |
| `collect`              | `Boolean`       | Aggregate all matches into a List/Set/Map (Default: `true`).               |
| `catchNearestCallTree` | `Boolean`       | `true`: Use immediate scope (lambdas); `false`: Use nearest named function. |

## Diagnostics & Rules

The plugin performs static analysis and will issue compiler errors in the following cases:

- E001/E002/E003: Attempting to use the plugin in init blocks, property initializers, or top-level code where no "caller parameters" exist.
- E004: No matching variable found for a non-nullable parameter.
- W001: Usage inside Local Functions (lambdas are supported, but local fun is discouraged due to synthetic closure conflicts).
- W002: Forgetting to provide = placeholder or a default value (required for the plugin to take over).

## How it works (The IR Level)

The plugin hooks into the Kotlin IR (Intermediate Representation) backend.

- It scans for function calls where parameters are marked @CaptureUpperArg and are currently using their default values.
- It looks up the IR tree to find the IrFunction currently being compiled (the "caller").
- It filters the caller's valueParameters based on your specified predicates.
- It rewrites the IrCall arguments, replacing the default value with an IrGetValue of the captured parameter.


















