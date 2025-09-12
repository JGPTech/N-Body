package kz.qwertukg.nBodyApp

val vertexShaderSource = """
        #version 410 core
        layout(location = 0) in vec3 aPos;

        uniform mat4 projection;
        uniform mat4 view;

        out float fragDistance; // Расстояние до камеры

        void main() {
            vec4 viewPosition = view * vec4(aPos, 1.0); // Позиция в пространстве камеры
            fragDistance = -viewPosition.z; // Z отрицательный в пространстве камеры
            gl_Position = projection * viewPosition; // Проекция на экран
        }
    """.trimIndent()

val geometryShaderSource = """
        #version 410 core
        layout(points) in;
        layout(triangle_strip, max_vertices = 18) out; // edges = 8

        uniform float w;         // Ширина экрана
        uniform float h;         // Высота экрана
        uniform float pointSize; // Радиус круга

        const int edges = 8; // Количество граней круга

        in float fragDistance[]; // Расстояние до камеры
        out float fragDistance2; // Для передачи во фрагментный шейдер

        void main() {
            fragDistance2 = fragDistance[0];
            vec4 center = gl_in[0].gl_Position;

            float radiusX = pointSize * h / w;
            float radiusY = pointSize;

            for (int i = 0; i <= edges; ++i) {
                float angle = i * 2.0 * 3.14159265359 / float(edges);
                vec2 offset = vec2(cos(angle) * radiusX, sin(angle) * radiusY);

                gl_Position = center;
                EmitVertex();

                gl_Position = center + vec4(offset, 0.0, 0.0);
                EmitVertex();
            }
            EndPrimitive();
        }
    """.trimIndent()

val fragmentShaderSource = """
        #version 410 core

        in float fragDistance2; // Расстояние до камеры

        uniform float zNear;
        uniform float zFar;

        out vec4 FragColor;

        void main() {
            float brightness = 1.0 - clamp((fragDistance2 - zNear) / (zFar - zNear) * 10, 0.0, 1.0);
            FragColor = vec4(vec3(brightness), 1.0);
        }
    """.trimIndent()


//val vertexShaderSource = """
//        #version 460 core
//        layout(location = 0) in vec3 aPos;
//        uniform mat4 projection;
//        uniform mat4 view;
//
//        void main() {
//            gl_Position = projection * view * vec4(aPos, 1.0);
//        }
//    """.trimIndent()
//
//// Фрагментный шейдер
//val fragmentShaderSource = """
//        #version 460 core
//        out vec4 FragColor;
//
//        void main() {
//            FragColor = vec4(1.0, 1.0, 1.0, 1.0); // Белый цвет
//        }
//    """.trimIndent()