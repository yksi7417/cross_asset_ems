/*
 * ems-core — shared types, error model, and utility surfaces consumed by
 * every other Java module. NO upstream module dependencies.
 *
 * See java/README.md and 80_architecture/ for the layering contract.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(libs.agrona)
}
