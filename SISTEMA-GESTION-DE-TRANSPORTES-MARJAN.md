**SISTEMA DE GESTIÓN DE TRANSPORTES MARJAN**

**Resultado del levantamiento de requerimientos**  
Después de realizar la entrevista con el propietario de Transportes MARJAN, identificamos que la empresa requiere un sistema de información que permita integrar y controlar sus principales procesos administrativos y operativos, debido a que actualmente una parte importante de la información se maneja mediante hojas de cálculo, documentos impresos, mensajes y registros independientes.  
Durante el levantamiento observamos que el principal problema no consiste únicamente en registrar los viajes realizados, sino en poder relacionar toda la información que interviene antes, durante y después de cada servicio de transporte.  
El objetivo principal del proyecto será desarrollar un Sistema de Gestión de Transportes que permita administrar clientes, solicitudes de servicio, viajes, rutas, unidades, operadores, gastos, combustible, mantenimiento y la información administrativa derivada de cada operación.

**Clientes y solicitudes de servicio**  
Identificamos que uno de los elementos principales del negocio son los clientes, ya que un mismo cliente puede solicitar múltiples servicios de transporte a lo largo del tiempo.  
De cada cliente será necesario conservar información que permita identificarlo y mantener comunicación con él, incluyendo nombre o razón social, RFC, domicilio, teléfono, correo electrónico, persona de contacto y sus condiciones comerciales.  
Existen clientes ocasionales y clientes frecuentes. Algunos cuentan con condiciones especiales de crédito o tarifas previamente negociadas.

El proceso operativo inicia cuando un cliente solicita transportar mercancía desde un lugar de origen hasta un destino.  
Cada solicitud deberá recibir un folio que permita identificarla de manera única y darle seguimiento.   
Será necesario registrar información como fecha de solicitud, cliente, origen, destino, características de la mercancía, peso aproximado, fechas programadas de recolección y entrega, tarifa acordada y observaciones.  
También identificamos la necesidad de conocer el estado de cada solicitud durante su ciclo de vida. Una solicitud podría encontrarse solicitada, autorizada, programada, asignada, en tránsito, entregada, cancelada o cerrada.

**Rutas y viajes**  
Durante la entrevista identificamos que una solicitud de servicio posteriormente origina la programación de un viaje.  
Los viajes representan una de las operaciones centrales del sistema, ya que alrededor de ellos se concentra información correspondiente al cliente, ruta, unidad, operador y gastos.  
Será necesario conservar el origen y destino, kilometraje estimado, kilometraje realmente recorrido, fecha y hora de salida, fecha y hora de llegada y cualquier situación importante ocurrida durante el traslado.  
La empresa realiza algunas rutas frecuentemente, por lo que consideramos conveniente conservar información que permita identificar los recorridos habituales y posteriormente analizar sus costos, duración y rentabilidad.  
El sistema deberá conservar el historial de todos los viajes realizados, aun cuando posteriormente un cliente deje de trabajar con la empresa, un operador sea dado de baja o una unidad sea vendida.

**Unidades**  
Otro elemento fundamental identificado durante el levantamiento corresponde a las unidades de transporte.  
Transportes MARJAN dispone de diferentes vehículos, cada uno identificado internamente mediante un número económico.  
También será necesario conocer placas, marca, modelo, año, número de serie, tipo de unidad, capacidad de carga y kilometraje.  
Cada unidad deberá tener un estado que permita conocer si se encuentra disponible, asignada, en viaje, en mantenimiento, fuera de servicio o dada de baja.  
Una regla importante identificada consiste en evitar que una misma unidad pueda ser asignada a dos viajes que se realicen simultáneamente.  
También será necesario conservar el historial de utilización de cada vehículo para conocer posteriormente todos los viajes en los que participó.

**Operadores**  
La empresa cuenta con operadores responsables de conducir las unidades.  
Será necesario registrar sus datos generales y laborales, además de la información correspondiente a sus licencias y documentos.  
Entre los datos identificados se encuentran nombre, domicilio, teléfonos, contacto de emergencia, RFC, CURP, número de licencia, tipo de licencia y fecha de vencimiento.  
El sistema deberá permitir conocer si un operador se encuentra disponible, realizando un viaje, descansando, de vacaciones, incapacitado o dado de baja.  
También identificamos como regla de negocio que un operador no podrá encontrarse asignado simultáneamente a dos viajes.  
Además, el sistema deberá advertir cuando una licencia se encuentre próxima a vencer o haya perdido su vigencia.

**Asignación de viajes**  
Una vez autorizado un servicio, el personal responsable deberá realizar la asignación correspondiente.  
Para ello será necesario seleccionar una unidad disponible y un operador disponible.  
Durante esta operación el sistema deberá validar que la unidad pueda realizar el servicio y que el operador tenga la documentación necesaria vigente.  
El viaje deberá conservar la información de la unidad y operador asignados, así como las fechas, kilometrajes y demás datos correspondientes.  
Consideramos importante que las asignaciones históricas permanezcan almacenadas, ya que modificar posteriormente la situación actual de un operador o unidad no deberá alterar los viajes realizados anteriormente.

**Gastos y anticipos**  
Uno de los principales requerimientos encontrados consiste en determinar cuánto cuesta realmente realizar cada viaje.  
Durante un servicio pueden generarse diferentes tipos de gastos, entre ellos combustible, casetas, alimentos, estacionamiento, hospedaje, reparaciones, maniobras y permisos.  
Se identificó la necesidad de clasificar estos gastos para posteriormente realizar análisis.  
Algunos operadores reciben anticipos antes de comenzar el viaje. Al finalizar deberán comprobar los gastos realizados.  
El sistema deberá permitir comparar el importe entregado al operador contra los gastos comprobados para determinar si existe un saldo pendiente por comprobar, una cantidad que deba devolver o algún importe que la empresa deba reembolsarle.

**Combustible**  
Debido a que el combustible representa uno de los principales costos para la empresa, identificamos la necesidad de llevar un registro detallado de cada carga.  
Será necesario conocer la unidad, viaje, fecha, estación de servicio, litros cargados, precio por litro, importe y kilometraje registrado en el momento de la carga.  
Con esta información la empresa espera determinar el rendimiento de combustible de sus unidades y detectar posibles variaciones.  
Por lo tanto, posteriormente deberán poder realizarse consultas para comparar kilómetros recorridos contra litros consumidos.

**Mantenimiento**  
Durante el levantamiento se determinó que el sistema también deberá controlar el mantenimiento preventivo y correctivo de las unidades.  
Cada mantenimiento deberá quedar relacionado con la unidad correspondiente y registrar fecha, kilometraje, tipo de mantenimiento, trabajos realizados, proveedor o taller, costo y próxima fecha o kilometraje recomendado para servicio.  
Cuando una unidad presente una falla que impida su utilización deberá poder marcarse como fuera de servicio.  
Mientras permanezca en ese estado no deberá permitirse su asignación a nuevos viajes.  
Incidencias  
Los viajes pueden presentar acontecimientos no previstos, por lo que se requiere registrar incidencias.  
Entre ellas podrían encontrarse accidentes, fallas mecánicas, retrasos, cierres carreteros, daños a la mercancía o problemas relacionados con documentación.  
Cada incidencia deberá conservar información suficiente para identificar el viaje, fecha, hora, lugar, tipo de problema, descripción y acciones realizadas.

**Entregas**  
Cuando la mercancía llegue a su destino deberá registrarse la entrega.  
Será necesario conocer la fecha y hora real, persona que recibió y la evidencia correspondiente.  
En una fase posterior podrían incorporarse fotografías, documentos digitalizados o firmas electrónicas.  
Se identificó como regla que determinados servicios no podrán considerarse completamente cerrados hasta contar con la documentación requerida.

**Tarifas, facturación y cobranza**  
El precio de los servicios puede variar dependiendo del cliente, ruta, unidad utilizada y características particulares de la carga.  
Por ello será necesario conservar el precio autorizado para cada servicio, aunque posteriormente las tarifas generales sean modificadas.  
También deberá existir información relacionada con la facturación y cobranza.  
La empresa necesita conocer las facturas generadas, importes, fechas, vencimientos, pagos recibidos y saldos pendientes.  
Algunos clientes trabajan de contado y otros mediante crédito, por lo que deberán considerarse diferentes condiciones comerciales.

**Usuarios y seguridad**  
Durante la entrevista identificamos que no todos los usuarios deberán disponer de los mismos permisos.  
Existirán diferentes responsabilidades relacionadas con administración, tráfico, mantenimiento, cobranza y consulta.  
Por esta razón será necesario manejar usuarios, perfiles y permisos.  
Las operaciones importantes deberán conservar información sobre quién las realizó y cuándo.  
También se determinó que la información histórica no deberá eliminarse fácilmente. Cuando sea posible, las operaciones deberán manejar estados como cancelado o dado de baja en lugar de borrar físicamente los registros.

**Reportes e información gerencial**  
Finalmente, identificamos que uno de los principales objetivos del sistema consiste en transformar la información operativa en información útil para la toma de decisiones.  
El propietario desea conocer cuáles clientes generan mayores ingresos, qué rutas son más utilizadas, cuáles unidades realizan más viajes, cuáles consumen más combustible, cuánto cuesta cada servicio y cuál es aproximadamente su rentabilidad.  
También requiere identificar clientes con adeudos, facturas vencidas, unidades disponibles, vehículos próximos a mantenimiento y operadores con documentación próxima a vencer.  
Los reportes deberán permitir diferentes criterios de búsqueda y periodos de consulta, así como la posibilidad de exportar determinada información.

**Conclusión del levantamiento**  
Como resultado de la entrevista concluimos que el sistema deberá construirse alrededor de la operación completa del  
servicio de transporte y no solamente del registro de viajes.  
La información deberá mantener relaciones que permitan reconstruir posteriormente toda la historia de una operación: quién solicitó el servicio, qué se transportó, de dónde salió, hacia dónde se dirigió, qué unidad se utilizó, qué operador realizó el viaje, qué gastos se generaron, cuánto combustible se consumió, cuándo fue entregada la mercancía, cuánto se cobró al cliente y cuál fue el resultado económico de la operación.  
A partir de este levantamiento será necesario realizar el análisis de la información para identificar las entidades que formarán parte del modelo de datos, determinar sus atributos, establecer sus identificadores y definir las relaciones y cardinalidades  
existentes entre ellas.

