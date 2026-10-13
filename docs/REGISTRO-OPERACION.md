# Registro de operaciones: modelo separado

1. Ejecutar operaciones-esquema-v2.sql en MySQL.
2. Opcionalmente ejecutar operaciones-demo.sql para crear 10 registros.
3. Reiniciar el backend y consultar /api/operaciones/resumen?page=0&size=10.

Tablas: registro_operaciones_v2 (cabecera), personas_operacion (copias historicas),
operaciones_v2 (datos financieros) y beneficiarios_operacion_v2.
id_usuario referencia usuarios.id. Las referencias a personas ya no usan IDs de tipos de documento.
El documento se conserva como texto del DTO. Cada registro crea nuevas copias de personas.

POST /api/operaciones/insertar-operacion guarda todo en una transaccion,
genera numeroRegistro con OP- y UUID y devuelve HTTP 201 con id, numeroRegistro y mensaje.
Valida la correspondencia entre usuarioId y usuario y el monto DECIMAL(15,2).
La persona representada es opcional.
El resumen conserva sus campos JSON; id y fecha corresponden a la cabecera.

Las tablas antiguas permanecen intactas. Los scripts no copian los datos antiguos:
el resumen nuevo solo muestra datos nuevos. Migrar el historial es un paso separado.
No se ejecuto SQL contra la base de datos desde el agente.
Ejecutar el DDL suministrado aunque ddl-auto este en update para crear todas las FK.
