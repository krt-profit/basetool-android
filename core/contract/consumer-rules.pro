# Consumer ProGuard/R8 rules for :core:contract.
#
# None needed today. The generated models are plain @Serializable data classes, and the
# kotlinx.serialization Gradle plugin already contributes the keep rules its generated
# serializers need; adding them again here would be a second copy that can only disagree.
#
# What would need a rule is anything reflective — a `Json` configured with a polymorphic
# serializer resolved by class name, say. There is none, and the file exists so that adding
# one has an obvious home rather than landing in the app module.
