/*
 * Copyright (c) 2025 KotlinCrypto
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

/* https://youtrack.jetbrains.com/issue/KT-75722 */
#ifndef KMP_PROCESS_SYS_H
#define KMP_PROCESS_SYS_H

#ifdef __ANDROID__
int __SYS_pipe2();
#endif /* !defined(__ANDROID__) */

#endif /* !defined(KMP_PROCESS_SYS_H) */
