{{- define "nifi.jvmCompat" -}}
{{- if .Values.jvmMemory }}
{{- $_ := set .Values.jvm "heapSize" .Values.jvmMemory -}}
{{- end }}
{{- end }}

{{- define "nifi.jvmDeprecationWarning" -}}
{{- if .Values.jvmMemory }}
################################################################################
# DEPRECATION WARNING:
# 'jvmMemory' is deprecated and will be removed in a future release.
# Please migrate to 'jvm.heapSize' in your values.yaml:
#
#   Old:
#     jvmMemory: {{ .Values.jvmMemory }}
#
#   New:
#     jvm:
#       heapSize: {{ .Values.jvmMemory }}
#
################################################################################
{{- end }}
{{- end }}
