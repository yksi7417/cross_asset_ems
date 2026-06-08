// Compile-verify for all five generated FSM headers.
// Including all five in one TU exercises overload resolution and catches
// any ODR violations or name collisions across the generated set.
#include <ems_fsm/ems_fsm.hpp>

using namespace crossasset::ems::fsm;

int main() {
    {
        OrderFsmContext ctx;
        auto r = transition(OrderFsmState::PENDING_NEW, OrderFsmEvent::ValidationPassed, ctx);
        (void)r;
    }
    {
        RouteFsmContext ctx;
        auto r = transition(RouteFsmState::PENDING, RouteFsmEvent::RouteSent, ctx);
        (void)r;
    }
    {
        MultiLegFsmContext ctx;
        auto r = transition(MultiLegFsmState::STAGED, MultiLegFsmEvent::LegsValidated, ctx);
        (void)r;
    }
    {
        VenueSessionFsmContext ctx;
        auto r = transition(VenueSessionFsmState::DISCONNECTED, VenueSessionFsmEvent::ConnectRequested, ctx);
        (void)r;
    }
    {
        SorFsmContext ctx;
        auto r = transition(SorFsmState::PENDING, SorFsmEvent::SorStrategyDecided, ctx);
        (void)r;
    }
    return 0;
}
