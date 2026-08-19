# Consumer rules for :core:data.
#
# The DTOs in this module are deserialised by kotlinx.serialization through generated serializers,
# which R8 reaches from the @Serializable classes themselves — no keep rule is needed for them.
# This file exists so the module declares its own consumer rules rather than inheriting whatever a
# future dependency ships, and so a rule that does become necessary has an obvious home.
