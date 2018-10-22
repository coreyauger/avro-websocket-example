const avro = require('avsc');

/** A UUID.  Note this is not a */
export type UUID = string;
export type ProviderKey = string;

export const zeroId = "00000000-0000-0000-0000-000000000000"

export interface Model{
}

export interface Ingredient extends Model{
    name: string,
    sugar: number,
    fat: number    
}

export interface Pizza extends Model{
    name: string,
    ingredients: Ingredient[],
    vegetarian: boolean,
    vegan: boolean,
    calories: number
}

export interface SEvent extends Model{
    correlationId: string,
    payload: Model
}

//final case class Pizza(name: String, ingredients: Seq[Ingredient], vegetarian: Boolean, vegan: Boolean, calories: Int) extends Model

const pizza1 = {
  name: "yum yum",
  ingredients: [
      {name: "tomato", sugar: 10, fat: 2},
      {name: "cheese", sugar: 10, fat: 20}
  ],
  vegetarian: true,
  vegan: false,
  calories: 225
} as Pizza

const sevent1 = {
    correlationId: "testing",
    payload: { ["m.Pizza"]: pizza1 }
} as SEvent

const pizzaType = avro.Type.forValue(pizza1);
//const sType = avro.Type.forValue(sevent1);

const pzType = avro.Type.forSchema({
    "type": "record",
    "name": "SEvent",
    "namespace": "m",
    "fields": [
        {
            "name": "correlationId",
            "type": "string"
        },
        {
            "name": "payload",
            "type": [
                {
                    "type": "record",
                    "name": "Dog",
                    "fields": [
                        {
                            "name": "name",
                            "type": "string"
                        }
                    ]
                },
                {
                    "type": "record",
                    "name": "Ingredient",
                    "fields": [
                        {
                            "name": "name",
                            "type": "string"
                        },
                        {
                            "name": "sugar",
                            "type": "double"
                        },
                        {
                            "name": "fat",
                            "type": "double"
                        }
                    ]
                },
                {
                    "type": "record",
                    "name": "Pizza",
                    "fields": [
                        {
                            "name": "name",
                            "type": "string"
                        },
                        {
                            "name": "ingredients",
                            "type": {
                                "type": "array",
                                "items": "Ingredient"
                            }
                        },
                        {
                            "name": "vegetarian",
                            "type": "boolean"
                        },
                        {
                            "name": "vegan",
                            "type": "boolean"
                        },
                        {
                            "name": "calories",
                            "type": "int"
                        }
                    ]
                },
                "SEvent"
            ]
        }
    ]
})

// We can use `type` to encode any values with the same structure:
const bufs = [
    pzType.toBuffer(sevent1)
];

console.log("bufs: ", bufs)

console.log("back: ", pzType.fromBuffer(bufs[0]))

const ws = new WebSocket("ws://localhost:8181/v1/ws/56e32f7e-8350-4892-8c9c-0fd6faea36f8")
ws.onopen =  (event) => {
    console.log("WEBSOCKET IS CONNECTED !!!")
    //ws.send("Here's some text that the server is urgently awaiting!"); 
    ws.send(pzType.toBuffer(sevent1))
};