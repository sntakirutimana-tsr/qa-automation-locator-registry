# Locator Registry

Standalone Java 21+ Maven library for resolving class-level `@LocatorSource` metadata
into immutable locator definitions. It has no Appium, Selenium, Spring, or
automation-framework dependency.

## Build

Building requires JDK 21 or newer; running the library requires Java 21 or newer.
Compilation uses `--release 21`.

```bash
mvn test
mvn package
```

Coverage can be generated without changing the POM:

```bash
mvn org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report
```

The HTML report is written to `target/site/jacoco/index.html`. The POM does not
automatically generate coverage or enforce coverage thresholds.

## Public API

```java
@LocatorSource(path = "locators/mobile", key = "booking.yml", section = "fields")
public class FlightSearchPage {
}

RegistryEngine registry = new RegistryEngine();
ResolvedLocators locators = registry.resolve(FlightSearchPage.class);
LocatorDefinition origin = locators.get("origin");
```

The consumer must call `resolve` at its chosen lifecycle boundary. An annotation
does not execute automatically on construction; this library does not create page
objects or integrate with a framework lifecycle.

An engine can also receive its default directory, cache setting, and class loader:

```java
RegistryEngine registry = new RegistryEngine(
    "locators/mobile", false, FlightSearchPage.class.getClassLoader());
```

These settings are fixed for the engine's lifetime. A null class loader falls back
to the library class loader. The no-argument constructor uses the bundled configuration
and captures the current thread context class loader.

## Files, sections, and lookup names

- `path` is a classpath directory, not a file. Empty or whitespace-only uses the
  engine's default directory.
- `key` selects only a YAML file; it does not select or require a YAML root key.
  Relative subdirectories are supported, for example `booking/flights.yml`.
- An explicit `.yml` or `.yaml` extension selects exactly that file, without fallback.
  Without an extension, `.yaml` is tried before `.yml`.
- `section` is an optional dotted subtree path inside the document. All descendant
  locators are included, with names relative to that section.

For `path = "locators/mobile"` and `key = "booking.yml"`, the classpath resource is
`locators/mobile/booking.yml`. The equivalent is `path = "locators"` and
`key = "mobile/booking.yml"`. Resource paths are trimmed, stripped of one leading
slash, and lowercased; use lowercase physical filenames and directory names.

The file directly contains its locator groups, with no resource-path wrapper:

```yaml
fields:
  origin:
    strategy: accessibility-id
    value: origin
  destination:
    strategy: accessibility-id
    value: destination
actions:
  search:
    strategy: accessibility-id
    value: search_button
components:
  date-picker:
    fields:
      departure:
        strategy: id
        value: departure_date
```

With no section, lookups include `fields.origin`, `actions.search`, and
`components.date-picker.fields.departure`. With `section = "fields"`, use `origin`.
With `section = "components"`, use `date-picker.fields.departure`.

**Migration:** previous engine behavior retained full resource-qualified names and
filtered only direct section children. It now resolves file-local names and entire
subtrees. Remove resource-path wrapper mappings from YAML files and update lookups
to be relative to the selected scope.

## Names and definition validation

Logical YAML group and locator names are trimmed and lowercased using `Locale.ROOT`,
consistently with lookups. Dotted names, dotted sections, and dotted lookup paths are
supported. Normalized sibling duplicates and flattened-path collisions are rejected
rather than overwritten. For example, literal `fields.origin` cannot coexist with
nested `fields: { origin: ... }` if both define the same locator.

Each definition must contain exactly `strategy` and `value`, both strings. Attribute
names are case-sensitive; their order is irrelevant. Missing or extra attributes,
empty mappings, invalid values, and unsupported strategies fail explicitly.
Group names `strategy` and `value` are reserved at definition boundaries.
Values retain their case but have outer whitespace trimmed.
Trimming is intentional for every strategy, including text/name locators; do not
rely on leading or trailing whitespace as part of a locator value.

Nonrecursive YAML anchors and aliases are supported. Recursive mappings or collections
are rejected with a resource-loading exception naming the source, before registry
flattening. Shared aliases may define locators at distinct paths.

Supported strategies: `id`, `xpath`, `accessibility-id`, `class-name`, `tag-name`,
`css-selector`, `name`, `link-text`, `partial-link-text`, and `android-uiautomator`.
Strategy names are case-insensitive; hyphens and whitespace are converted to underscores.

## Configuration

Bundled defaults live at `qa/automation/locator/config/registry-defaults.yml` and load
through the library's defining class loader. Consumer `application.yml` and
`application.yaml` files are not used as library defaults.

The no-argument engine constructor uses configuration resolved once, on first
configuration access, in this order: JVM system property, OS environment variable,
bundled default. Blank overrides are ignored. `.env` files are **not** read.

| Purpose | JVM property | Environment variable | Default |
|---|---|---|---|
| Default directory | `locator.registry.path.default` | `LOCATOR_REGISTRY_DEFAULT_PATH` | `locator-registry` |
| Cache flattened documents | `locator.registry.cache-enabled` | `LOCATOR_REGISTRY_CACHE_ENABLED` | `true` |

An explicit nonblank annotation `path` takes precedence over the engine's directory.
The explicit engine constructor overrides the default directory and cache setting
without changing global configuration. The bundled validation rules still apply.

## Exceptions and lifecycle

Library domain exceptions extend `RegistryBaseException`, an unchecked exception.
Subclasses distinguish annotation, configuration, resource-not-found, resource-loading,
mapping, definition, strategy, section-not-found, and locator-not-found errors.
Required null API arguments fail with `NullPointerException`.
`isResourceKeyValid(null)` returns `false`. `ResourceDataCast.asMap` validates that
the input is a mapping with string keys and returns an ordered, immutable shallow
copy; invalid map types or keys raise `IllegalLocatorRegistryArgumentException`.
The legacy `LocatorKeyNotFoundException` remains available but is deprecated:
annotation keys select files, so missing files use `LocatorRegistryResourceNotFoundException`.

Registry and configuration loading use composition around `YamlResourceLoader`.
The SLF4J dependency is retained for the existing `{}` diagnostic-message formatting;
the library does not configure logging or require a logging provider.

Classpath YAML is read through streams, supporting unpacked directories and packaged
JARs without extracting resources or mounting a filesystem.

Each `RegistryEngine` owns a thread-safe cache of immutable flattened documents.
Caches are not shared across engines or class loaders. Changing the thread context
class loader does not change an existing engine's resource source. Reuse an engine
to reuse its cache; create a new engine to load fresh documents. Failed loads are
not cached. Disabling caching reloads on each resolution.
